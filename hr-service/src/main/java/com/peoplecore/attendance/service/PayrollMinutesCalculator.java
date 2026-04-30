package com.peoplecore.attendance.service;

import com.peoplecore.attendance.entity.CommuteRecord;
import com.peoplecore.attendance.entity.HolidayReason;
import com.peoplecore.attendance.entity.OvertimeRequest;
import com.peoplecore.attendance.entity.WorkGroup;
import com.peoplecore.attendance.repository.CommuteRecordRepository;
import com.peoplecore.attendance.repository.OvertimeRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/*
 * CommuteRecord 급여 연동 분 계산 유틸.
 *
 * 두 진입점:
 *  - computeForCheckout(record, checkOut) : 체크아웃 시. 산출만 반환 (mutate/persist 없음).
 *                                            호출부가 native UPDATE 로 일괄 영속화.
 *  - applyApprovedRecognition(record)     : OT 승인 / 정정 승인 시. native UPDATE 로 분 컬럼 6종 갱신.
 *
 * 정책:
 *  - APPROVAL: OT 결재 APPROVED 확정 후에만 recognized_* 에 값 배정 (야간/휴일/연장 분해)
 *  - ALL:      결재 무관 체크아웃 시점에 overtime 전체 인정
 */
@Component
@Slf4j
public class PayrollMinutesCalculator {

    private static final LocalTime NIGHT_START = LocalTime.of(22, 0);
    private static final LocalTime NIGHT_END = LocalTime.of(6, 0);

    private final OvertimeRequestRepository overtimeRequestRepository;
    private final CommuteRecordRepository commuteRecordRepository;

    @Autowired
    public PayrollMinutesCalculator(OvertimeRequestRepository overtimeRequestRepository,
                                    CommuteRecordRepository commuteRecordRepository) {
        this.overtimeRequestRepository = overtimeRequestRepository;
        this.commuteRecordRepository = commuteRecordRepository;
    }

    /* 분 컬럼 산출 결과 - native UPDATE 호출부에 전달 */
    public record PayrollMinutes(long actual, long overtime, long unrecognizedOt,
                                 long recExt, long recNight, long recHoliday) {
        public static final PayrollMinutes ZERO = new PayrollMinutes(0L, 0L, 0L, 0L, 0L, 0L);
    }

    /* 인정 분 재산출 호출 출처 - 분기 로직에 영향 */
    public enum RecognitionSource {
        /* OT 결재 승인/이탈 - APPROVAL 그룹은 APPROVED OvertimeRequest 구간만 인정 */
        OT_REQUEST,
        /* 정정 결재 승인 - 사실관계 인정으로 APPROVAL 그룹에서도 overtime 전체 인정 */
        ATTENDANCE_MODIFY
    }

    /**
     * 체크아웃 직후 호출 - 분 컬럼 6종 산출 (mutate/persist 없음).
     * 호출부(CommuteService.checkOut)는 반환값과 함께 9컬럼 native UPDATE 발행.
     * APPROVAL 그룹: recognized_* = 0 / ALL 그룹: overtime 전체 자동 인정.
     */
    public PayrollMinutes computeForCheckout(CommuteRecord record, LocalDateTime checkOut) {
        WorkGroup wg = record.getEmployee().getWorkGroup();
        if (wg == null || wg.getGroupStartTime() == null || wg.getGroupEndTime() == null
                || record.getComRecCheckIn() == null || checkOut == null) {
            return PayrollMinutes.ZERO;
        }
        LocalDateTime checkIn = record.getComRecCheckIn();
        LocalDate workDate = record.getWorkDate();

        long actual = calcActualWorkMinutes(checkIn, checkOut, workDate, wg);
        long overtime = calcOvertimeMinutes(checkIn, checkOut, workDate, wg);

        long recExt = 0L, recNight = 0L, recHoliday = 0L;
        // ALL 그룹: 결재 없이도 overtime 전체 인정
        if (overtime > 0 && wg.getGroupOvertimeRecognize() == WorkGroup.GroupOvertimeRecognize.ALL) {
            long[] rec = allocateRecognizedAll(checkIn, checkOut, workDate, wg, overtime, record.getHolidayReason());
            recExt = rec[0]; recNight = rec[1]; recHoliday = rec[2];
        }
        long unrecognizedOt = overtime - recExt - recHoliday;
        validatePayrollInvariants(actual, overtime, unrecognizedOt, recExt, recNight, recHoliday);
        log.debug("[Payroll-checkout] comRecId={}, recognize={}, actual={}, ot={}, unrecognizedOt={}, ext={}, night={}, holiday={}",
                record.getComRecId(), wg.getGroupOvertimeRecognize(),
                actual, overtime, unrecognizedOt, recExt, recNight, recHoliday);
        return new PayrollMinutes(actual, overtime, unrecognizedOt, recExt, recNight, recHoliday);
    }

    /**
     * OT 승인 / 근태 정정 승인 시 호출. actual/overtime 재계산 후 인정 분 분배.
     * 파티션 규칙 준수: 엔티티 mutate 대신 native UPDATE (work_date 포함).
     *
     * 인정 분배 분기:
     *  - ALL 그룹: source 무관, overtime 전체 인정 (출퇴근만으로 자동)
     *  - APPROVAL 그룹 + ATTENDANCE_MODIFY: 정정 결재 통과 → overtime 전체 인정
     *  - APPROVAL 그룹 + OT_REQUEST: APPROVED OvertimeRequest 구간만 인정
     */
    public void applyApprovedRecognition(CommuteRecord record, RecognitionSource source) {
        WorkGroup wg = record.getEmployee().getWorkGroup();
        if (wg == null || wg.getGroupStartTime() == null || wg.getGroupEndTime() == null
                || record.getComRecCheckIn() == null || record.getComRecCheckOut() == null) {
            return;
        }
        LocalDateTime checkIn = record.getComRecCheckIn();
        LocalDateTime checkOut = record.getComRecCheckOut();
        LocalDate workDate = record.getWorkDate();

        long actual = calcActualWorkMinutes(checkIn, checkOut, workDate, wg);
        long overtime = calcOvertimeMinutes(checkIn, checkOut, workDate, wg);

        long recExt = 0L, recNight = 0L, recHoliday = 0L;
        if (overtime > 0) {
            /* 그룹 정책상 자동 인정 (ALL) */
            boolean autoRecognizeByGroup =
                    wg.getGroupOvertimeRecognize() == WorkGroup.GroupOvertimeRecognize.ALL;
            /* 정정 결재 통과 → 사실관계 인정 */
            boolean recognizeByModifyApproval = source == RecognitionSource.ATTENDANCE_MODIFY;
            long[] rec = (autoRecognizeByGroup || recognizeByModifyApproval)
                    ? allocateRecognizedAll(checkIn, checkOut, workDate, wg, overtime, record.getHolidayReason())
                    : allocateRecognizedByApprovedOt(record, wg);
            recExt = rec[0]; recNight = rec[1]; recHoliday = rec[2];
        }
        long unrecognizedOt = overtime - recExt - recHoliday;
        validatePayrollInvariants(actual, overtime, unrecognizedOt, recExt, recNight, recHoliday);

        int updated = commuteRecordRepository.applyPayrollMinutes(
                record.getComRecId(), workDate,
                actual, overtime, unrecognizedOt, recExt, recNight, recHoliday);
        if (updated != 1) {
            throw new IllegalStateException(
                    "PayrollMinutes UPDATE 실패 - comRecId=" + record.getComRecId() + ", affected=" + updated);
        }
        log.debug("[Payroll-recog] comRecId={}, recognize={}, source={}, actual={}, ot={}, unrecognizedOt={}, ext={}, night={}, holiday={}",
                record.getComRecId(), wg.getGroupOvertimeRecognize(), source,
                actual, overtime, unrecognizedOt, recExt, recNight, recHoliday);
    }

    /**
     * ALL 그룹용 배정 - overtime 전체를 휴일/평일에 따라 recHoliday/recExt 한쪽에 배정.
     * recNight 은 [otStart, checkOut] 과 야간 윈도우 교집합.
     */
    private long[] allocateRecognizedAll(LocalDateTime checkIn, LocalDateTime checkOut, LocalDate workDate,
                                         WorkGroup wg, long overtimeMin, HolidayReason holidayReason) {
        LocalDateTime groupEndDt = workDate.atTime(wg.getGroupEndTime());
        LocalDateTime otStart = checkIn.isAfter(groupEndDt) ? checkIn : groupEndDt;
        long recExt = 0L, recHoliday = 0L;
        if (holidayReason != null) {
            recHoliday = overtimeMin;
        } else {
            recExt = overtimeMin;
        }
        long recNight = nightOverlapMinutes(otStart, checkOut);
        return new long[]{recExt, recNight, recHoliday};
    }

    /**
     * APPROVAL 그룹용 배정 - 해당 날짜 APPROVED OT 전부 조회 → overtime 구간과 교집합 합산 → 휴일/평일 분배 + 야간 교집합.
     * (OT 결재 DB 조회 필요 → CommuteRecord 기반 유지)
     */
    private long[] allocateRecognizedByApprovedOt(CommuteRecord record, WorkGroup wg) {
        LocalDateTime checkIn = record.getComRecCheckIn();
        LocalDateTime checkOut = record.getComRecCheckOut();
        LocalDate workDate = record.getWorkDate();
        LocalDateTime groupEndDt = workDate.atTime(wg.getGroupEndTime());
        LocalDateTime otStart = checkIn.isAfter(groupEndDt) ? checkIn : groupEndDt;

        List<LocalDateTime[]> segs = new ArrayList<>();
        long recognizedTotal = 0L;
        List<OvertimeRequest> approved = overtimeRequestRepository
                .findApprovedByEmpAndDateRange(
                        record.getEmployee().getEmpId(),
                        workDate.atStartOfDay(),
                        workDate.atTime(LocalTime.MAX));
        for (OvertimeRequest ot : approved) {
            LocalDateTime segS = max(otStart, ot.getOtPlanStart());
            LocalDateTime segE = min(checkOut, ot.getOtPlanEnd());
            if (segE.isAfter(segS)) {
                segs.add(new LocalDateTime[]{segS, segE});
                recognizedTotal += Duration.between(segS, segE).toMinutes();
            }
        }
        long recExt = 0L, recHoliday = 0L;
        if (record.getHolidayReason() != null) {
            recHoliday = recognizedTotal;
        } else {
            recExt = recognizedTotal;
        }
        long recNight = 0L;
        for (LocalDateTime[] seg : segs) {
            recNight += nightOverlapMinutes(seg[0], seg[1]);
        }
        return new long[]{recExt, recNight, recHoliday};
    }

    /* ==================== 내부 공통 ==================== */

    /** actual = (checkOut - checkIn) - 휴게 교집합 */
    private long calcActualWorkMinutes(LocalDateTime checkIn, LocalDateTime checkOut,
                                       LocalDate workDate, WorkGroup wg) {
        LocalDateTime breakStart = (wg.getGroupBreakStart() != null) ? workDate.atTime(wg.getGroupBreakStart()) : null;
        LocalDateTime breakEnd = (wg.getGroupBreakEnd() != null) ? workDate.atTime(wg.getGroupBreakEnd()) : null;
        long gross = Duration.between(checkIn, checkOut).toMinutes();
        long breakOverlap = overlapMinutes(checkIn, checkOut, breakStart, breakEnd);
        return Math.max(0L, gross - breakOverlap);
    }

    /** overtime = 정시 종료 ~ checkOut (휴게 교집합 차감). checkIn 이 정시 이후면 checkIn 부터 */
    private long calcOvertimeMinutes(LocalDateTime checkIn, LocalDateTime checkOut,
                                     LocalDate workDate, WorkGroup wg) {
        LocalDateTime groupEndDt = workDate.atTime(wg.getGroupEndTime());
        if (!checkOut.isAfter(groupEndDt)) return 0L;
        LocalDateTime otStart = checkIn.isAfter(groupEndDt) ? checkIn : groupEndDt;
        LocalDateTime breakStart = (wg.getGroupBreakStart() != null) ? workDate.atTime(wg.getGroupBreakStart()) : null;
        LocalDateTime breakEnd = (wg.getGroupBreakEnd() != null) ? workDate.atTime(wg.getGroupBreakEnd()) : null;
        long gross = Duration.between(otStart, checkOut).toMinutes();
        long breakOverlap = overlapMinutes(otStart, checkOut, breakStart, breakEnd);
        return Math.max(0L, gross - breakOverlap);
    }

    /* 분 컬럼 불변식 검증 - 기존 CommuteRecord.applyPayrollMinutes 가드 인라인 */
    private void validatePayrollInvariants(long actual, long overtime, long unrecognizedOt,
                                           long recExt, long recNight, long recHoliday) {
        if (unrecognizedOt < 0 || unrecognizedOt > overtime) {
            throw new IllegalArgumentException(
                    "unrecognizedOtMinutes 범위 위반: unrecognizedOt=" + unrecognizedOt
                            + ", overtime=" + overtime);
        }
        long maxTyped = Math.max(recExt, Math.max(recNight, recHoliday));
        if (overtime < maxTyped) {
            throw new IllegalArgumentException(
                    "overtimeMinutes < max(recognized_*) 불변식 위반: overtime=" + overtime
                            + ", ext=" + recExt + ", night=" + recNight + ", holiday=" + recHoliday);
        }
        if (actual < overtime) {
            throw new IllegalArgumentException(
                    "actualWorkMinutes < overtimeMinutes 불변식 위반: actual=" + actual
                            + ", overtime=" + overtime);
        }
    }

    /** 두 구간 [aS,aE] ∩ [bS,bE] 분. null 포함 시 0 */
    private long overlapMinutes(LocalDateTime aS, LocalDateTime aE,
                                LocalDateTime bS, LocalDateTime bE) {
        if (aS == null || aE == null || bS == null || bE == null) return 0L;
        LocalDateTime s = max(aS, bS);
        LocalDateTime e = min(aE, bE);
        return e.isAfter(s) ? Duration.between(s, e).toMinutes() : 0L;
    }

    /** 구간 [s,e] 와 걸친 날짜들의 야간 윈도우(22:00~익일 06:00) 교집합 합산 */
    private long nightOverlapMinutes(LocalDateTime s, LocalDateTime e) {
        long total = 0L;
        LocalDate d = s.toLocalDate();
        while (!d.isAfter(e.toLocalDate())) {
            LocalDateTime nightStart = d.atTime(NIGHT_START);
            LocalDateTime nightEnd = d.plusDays(1).atTime(NIGHT_END);
            total += overlapMinutes(s, e, nightStart, nightEnd);
            d = d.plusDays(1);
        }
        return total;
    }

    private LocalDateTime max(LocalDateTime a, LocalDateTime b) { return a.isAfter(b) ? a : b; }
    private LocalDateTime min(LocalDateTime a, LocalDateTime b) { return a.isBefore(b) ? a : b; }
}

package com.peoplecore.attendance.service;

import com.peoplecore.attendance.entity.CommuteRecord;
import com.peoplecore.attendance.entity.OvertimeRequest;
import com.peoplecore.attendance.entity.WorkGroup;
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

/**
 * CommuteRecord 급여 연동 분 계산 유틸.
 *
 * 두 진입점:
 *  - applyCheckoutBase(record)  : 체크아웃 시. actual, overtime 만 계산. recognized_* = 0 고정.
 *  - applyApprovedRecognition(record) : OT 승인 이벤트 수신 시. actual/overtime 재계산 + recognized_* 산출.
 *
 * 정책:
 *  - 체크아웃 시점엔 "기록" 만 — 인정은 결재 승인 전까지 보류
 *  - OT 결재 APPROVED 확정 후에만 recognized_* 에 값 배정 (야간/휴일/연장 분해)
 */
@Component
@Slf4j
public class PayrollMinutesCalculator {

    private static final LocalTime NIGHT_START = LocalTime.of(22, 0);
    private static final LocalTime NIGHT_END = LocalTime.of(6, 0);

    private final OvertimeRequestRepository overtimeRequestRepository;

    @Autowired
    public PayrollMinutesCalculator(OvertimeRequestRepository overtimeRequestRepository) {
        this.overtimeRequestRepository = overtimeRequestRepository;
    }

    /**
     * 체크아웃 직후 호출.
     * actual_work_minutes, overtime_minutes 만 계산. recognized_* 는 0.
     */
    public void applyCheckoutBase(CommuteRecord record) {
        if (!isReady(record)) return;
        WorkGroup wg = record.getEmployee().getWorkGroup();
        long actual = calcActualWorkMinutes(record, wg);
        long overtime = calcOvertimeMinutes(record, wg);
        record.applyPayrollMinutes(actual, overtime, 0L, 0L, 0L);
        log.debug("[Payroll-base] comRecId={}, actual={}, overtime={}",
                record.getComRecId(), actual, overtime);
    }

    /**
     * OT 승인 이벤트 수신 시 호출.
     * actual/overtime 재계산 + 해당 날짜 APPROVED OT 전체와 overtime 구간 교집합으로 recognized_* 산출.
     * 복수 APPROVED 건 합산. 휴일 여부로 extended/holiday 분배. night 은 별도 교집합.
     */
    public void applyApprovedRecognition(CommuteRecord record) {
        if (!isReady(record)) return;
        WorkGroup wg = record.getEmployee().getWorkGroup();

        long actual = calcActualWorkMinutes(record, wg);
        long overtime = calcOvertimeMinutes(record, wg);

        long recExt = 0L, recNight = 0L, recHoliday = 0L;
        if (overtime > 0) {
            LocalDateTime checkIn = record.getComRecCheckIn();
            LocalDateTime checkOut = record.getComRecCheckOut();
            LocalDate workDate = record.getWorkDate();
            LocalDateTime groupEndDt = workDate.atTime(wg.getGroupEndTime());
            LocalDateTime otStart = checkIn.isAfter(groupEndDt) ? checkIn : groupEndDt;

            // 해당 날짜 APPROVED OT 와 OT 구간 교집합 수집
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

            // 휴일 vs 평일 분배 (holidayReason 이 있으면 전체 휴일)
            if (record.getHolidayReason() != null) {
                recHoliday = recognizedTotal;
            } else {
                recExt = recognizedTotal;
            }

            // 야간 교집합 — 각 recognized 구간에 [22:00~익일 06:00] 교집합 합산
            for (LocalDateTime[] seg : segs) {
                recNight += nightOverlapMinutes(seg[0], seg[1]);
            }
        }

        record.applyPayrollMinutes(actual, overtime, recExt, recNight, recHoliday);
        log.debug("[Payroll-recog] comRecId={}, actual={}, ot={}, ext={}, night={}, holiday={}",
                record.getComRecId(), actual, overtime, recExt, recNight, recHoliday);
    }

    /* ==================== 내부 공통 ==================== */

    private boolean isReady(CommuteRecord record) {
        if (record.getComRecCheckIn() == null || record.getComRecCheckOut() == null) return false;
        WorkGroup wg = record.getEmployee().getWorkGroup();
        return wg != null && wg.getGroupStartTime() != null && wg.getGroupEndTime() != null;
    }

    /** actual = (checkOut - checkIn) - 휴게 교집합 */
    private long calcActualWorkMinutes(CommuteRecord record, WorkGroup wg) {
        LocalDateTime checkIn = record.getComRecCheckIn();
        LocalDateTime checkOut = record.getComRecCheckOut();
        LocalDateTime breakStart = (wg.getGroupBreakStart() != null)
                ? record.getWorkDate().atTime(wg.getGroupBreakStart()) : null;
        LocalDateTime breakEnd = (wg.getGroupBreakEnd() != null)
                ? record.getWorkDate().atTime(wg.getGroupBreakEnd()) : null;
        long gross = Duration.between(checkIn, checkOut).toMinutes();
        long breakOverlap = overlapMinutes(checkIn, checkOut, breakStart, breakEnd);
        return Math.max(0L, gross - breakOverlap);
    }

    /** overtime = 정시 종료 ~ checkOut (휴게 교집합 차감). checkIn 이 정시 이후면 checkIn 부터 */
    private long calcOvertimeMinutes(CommuteRecord record, WorkGroup wg) {
        LocalDateTime checkIn = record.getComRecCheckIn();
        LocalDateTime checkOut = record.getComRecCheckOut();
        LocalDateTime groupEndDt = record.getWorkDate().atTime(wg.getGroupEndTime());
        if (!checkOut.isAfter(groupEndDt)) return 0L;

        LocalDateTime otStart = checkIn.isAfter(groupEndDt) ? checkIn : groupEndDt;
        LocalDateTime breakStart = (wg.getGroupBreakStart() != null)
                ? record.getWorkDate().atTime(wg.getGroupBreakStart()) : null;
        LocalDateTime breakEnd = (wg.getGroupBreakEnd() != null)
                ? record.getWorkDate().atTime(wg.getGroupBreakEnd()) : null;
        long gross = Duration.between(otStart, checkOut).toMinutes();
        long breakOverlap = overlapMinutes(otStart, checkOut, breakStart, breakEnd);
        return Math.max(0L, gross - breakOverlap);
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

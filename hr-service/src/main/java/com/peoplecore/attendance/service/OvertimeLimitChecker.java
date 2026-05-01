package com.peoplecore.attendance.service;

import com.peoplecore.attendance.entity.OtExceedAction;
import com.peoplecore.attendance.entity.OvertimePolicy;
import com.peoplecore.attendance.entity.WorkGroup;
import com.peoplecore.attendance.repository.CommuteRecordRepository;
import com.peoplecore.attendance.repository.OverTimePolicyRepository;
import com.peoplecore.attendance.repository.OvertimeRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/* 주간 근무시간 한도 검증 - OvertimePolicy.otExceedAction 기반 */
/* OT 신청 + 정정 신청 양쪽이 사용. 정정은 적용 후 추정값까지 합산해서 검증 */
/* readOnly = true: 여러 쿼리 일관 스냅샷 + Hibernate flush 모드 MANUAL 최적화 */
@Component
@Transactional(readOnly = true)
public class OvertimeLimitChecker {

    /* 정책 미존재 회사 fallback - 52h */
    private static final int DEFAULT_WEEKLY_MAX_MINUTE = 3120;

    private final OverTimePolicyRepository overTimePolicyRepository;
    private final CommuteRecordRepository commuteRecordRepository;
    private final OvertimeRequestRepository overtimeRequestRepository;

    @Autowired
    public OvertimeLimitChecker(OverTimePolicyRepository overTimePolicyRepository,
                                CommuteRecordRepository commuteRecordRepository,
                                OvertimeRequestRepository overtimeRequestRepository) {
        this.overTimePolicyRepository = overTimePolicyRepository;
        this.commuteRecordRepository = commuteRecordRepository;
        this.overtimeRequestRepository = overtimeRequestRepository;
    }

    /* 정책 + 현재 그 주 사용 분 - prefill 응답용 (정정 미반영) */
    public WeeklyUsage usageBefore(UUID companyId, Long empId, LocalDate workDate) {
        PolicyView pv = loadPolicy(companyId);
        WeekRange wr = WeekRange.of(workDate);

        long actualSum = nullToZero(commuteRecordRepository
                .sumRecognizedWorkMinutesInWeek(companyId, empId, wr.monday, wr.sunday));
        long otSum = nullToZero(overtimeRequestRepository
                .sumFuturePlanMinutesInWeek(empId, LocalDateTime.now(),
                        wr.monday.atStartOfDay(), wr.sunday.atTime(LocalTime.MAX)));

        return new WeeklyUsage(pv.weeklyMax, actualSum + otSum, pv.action);
    }

    /* OT 신청 결재 통과 후 검증용 - 그 주 인정 근무 + 미래 OT plan + 신청 본 건의 plan */
    public WeeklyUsage usageWithNewOt(UUID companyId, Long empId, LocalDate workDate,
                                      LocalDateTime newOtPlanStart, LocalDateTime newOtPlanEnd) {
        PolicyView pv = loadPolicy(companyId);
        WeekRange wr = WeekRange.of(workDate);

        long actualSum = nullToZero(commuteRecordRepository
                .sumRecognizedWorkMinutesInWeek(companyId, empId, wr.monday, wr.sunday));
        long otSum = nullToZero(overtimeRequestRepository
                .sumFuturePlanMinutesInWeek(empId, LocalDateTime.now(),
                        wr.monday.atStartOfDay(), wr.sunday.atTime(LocalTime.MAX)));
        long newOtMin = (newOtPlanStart != null && newOtPlanEnd != null
                        && newOtPlanEnd.isAfter(newOtPlanStart))
                ? Duration.between(newOtPlanStart, newOtPlanEnd).toMinutes() : 0L;

        return new WeeklyUsage(pv.weeklyMax, actualSum + otSum + newOtMin, pv.action);
    }

    /* 정정 적용 후 추정 합계 - consumer 검증용 */
    /* 다른 일자 actualWork 합 + 정정 후 해당 일자 (newCheckOut-newCheckIn-groupBreak) + 그 주 OT 합 */
    public WeeklyUsage usageAfterModify(UUID companyId, Long empId, WorkGroup wg, LocalDate workDate,
                                        LocalDateTime newCheckIn, LocalDateTime newCheckOut) {
        PolicyView pv = loadPolicy(companyId);
        WeekRange wr = WeekRange.of(workDate);

        long otherDaysActual = nullToZero(commuteRecordRepository
                .sumRecognizedWorkMinutesInWeekExcluding(companyId, empId, wr.monday, wr.sunday, workDate));
        long modifiedActual = estimateActualMinutes(newCheckIn, newCheckOut, wg);
        long otSum = nullToZero(overtimeRequestRepository
                .sumFuturePlanMinutesInWeek(empId, LocalDateTime.now(),
                        wr.monday.atStartOfDay(), wr.sunday.atTime(LocalTime.MAX)));

        return new WeeklyUsage(pv.weeklyMax, otherDaysActual + modifiedActual + otSum, pv.action);
    }

    /* 정책 조회 + fallback - 회사당 1건이라 캐시 안 함 (호출 빈도 낮음) */
    private PolicyView loadPolicy(UUID companyId) {
        OvertimePolicy policy = overTimePolicyRepository.findByCompany_CompanyId(companyId).orElse(null);
        int weeklyMax = (policy != null) ? policy.getOtPolicyWeeklyMaxMinutes() : DEFAULT_WEEKLY_MAX_MINUTE;
        OtExceedAction action = (policy != null) ? policy.getOtExceedAction() : OtExceedAction.NOTIFY;
        return new PolicyView(weeklyMax, action);
    }

    /* (newCheckOut - newCheckIn) - groupBreak. null/역전 시 0 */
    private long estimateActualMinutes(LocalDateTime checkIn, LocalDateTime checkOut, WorkGroup wg) {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) return 0L;
        long span = Duration.between(checkIn, checkOut).toMinutes();
        long breakMin = (wg != null && wg.getGroupBreakStart() != null && wg.getGroupBreakEnd() != null)
                ? Duration.between(wg.getGroupBreakStart(), wg.getGroupBreakEnd()).toMinutes() : 0L;
        return Math.max(0L, span - breakMin);
    }

    private long nullToZero(Long v) { return v == null ? 0L : v; }

    /* 검증 결과 - prefill 응답 채움 + consumer 알림/반려 분기 */
    public record WeeklyUsage(int weeklyMaxMinutes, long usedMinutes, OtExceedAction exceedAction) {
        public boolean isExceeded() { return usedMinutes > weeklyMaxMinutes; }
    }

    /* 내부 헬퍼 - 정책 + fallback 조합 */
    private record PolicyView(int weeklyMax, OtExceedAction action) {}

    /* 내부 헬퍼 - 주간 범위 계산 (월~일) */
    private record WeekRange(LocalDate monday, LocalDate sunday) {
        static WeekRange of(LocalDate date) {
            LocalDate monday = date.with(DayOfWeek.MONDAY);
            return new WeekRange(monday, monday.plusDays(6));
        }
    }
}

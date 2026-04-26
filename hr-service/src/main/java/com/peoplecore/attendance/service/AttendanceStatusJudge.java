package com.peoplecore.attendance.service;

import com.peoplecore.attendance.dto.AttendanceAdminRow;
import com.peoplecore.attendance.entity.AttendanceCardType;
import com.peoplecore.attendance.entity.WorkStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/*
 * AttendanceAdminRow 한 건의 카드 상태(복수)를 판정.
 *
 * WorkStatus 분기 — EnumMap WS_STRATEGY (상태 패턴)
 *  · true  반환 : 조기 반환 (이후 조건 카드 불필요 — ex. ABSENT)
 *  · false 반환 : 조건 카드 이어서 처리
 *
 * 조건 카드 (런타임 값 기반):
 *  - VACATION_ATTEND : 오늘 APPROVED 휴가 + 체크인 존재
 *  - MISSING_COMMUTE : (a) 근무예정일인데 CommuteRecord 없음  (b) 체크인 있고 퇴근시각 지나도 체크아웃 없음
 *  - UNDER_MIN_HOUR  : 체크아웃 완료 + 실근무분 < 소정근무분
 *  - UNAPPROVED_OT   : 체크아웃 > groupEndTime + 승인 OT 없음
 *  - MAX_HOUR_EXCEED : weekWorkedMinutes > weeklyMaxMinutes
 *  - NORMAL          : 체크인 있고 위 이상 상태 모두 해당 없음
 */
@Component
public class AttendanceStatusJudge {

    /* WorkStatus → 카드 추가 전략. true = 조기 반환 */
    @FunctionalInterface
    private interface WorkStatusStrategy {
        boolean resolve(AttendanceAdminRow r, List<AttendanceCardType> out);
    }

    /* 등록 없는 WorkStatus 기본 동작 — 카드 추가 없이 조건 카드 처리로 진행 */
    private static final WorkStatusStrategy NO_OP = (r, out) -> false;

    private static final EnumMap<WorkStatus, WorkStatusStrategy> WS_STRATEGY = new EnumMap<>(WorkStatus.class);
    static {
        /* ABSENT: 승인 휴가 없으면 결근 카드 추가 후 조기 반환 */
        WS_STRATEGY.put(WorkStatus.ABSENT, (r, out) -> {
            if (!Boolean.TRUE.equals(r.getHasApprovedVacationToday())) {
                out.add(AttendanceCardType.ABSENT);
                return true;
            }
            return false; /* 휴가+ABSENT → 이어서 VACATION_ATTEND 처리 */
        });
        WS_STRATEGY.put(WorkStatus.LATE,
                (r, out) -> { out.add(AttendanceCardType.LATE); return false; });
        WS_STRATEGY.put(WorkStatus.EARLY_LEAVE,
                (r, out) -> { out.add(AttendanceCardType.EARLY_LEAVE); return false; });
        WS_STRATEGY.put(WorkStatus.LATE_AND_EARLY, (r, out) -> {
            out.add(AttendanceCardType.LATE);
            out.add(AttendanceCardType.EARLY_LEAVE);
            return false;
        });
        /* NORMAL / AUTO_CLOSED / HOLIDAY_WORK → NO_OP */
    }

    /* 카드 상태 배열 계산. weeklyMaxMinutes: 회사 정책 주간 최대 근무 분 */
    public List<AttendanceCardType> judge(AttendanceAdminRow r, LocalDate date, int weeklyMaxMinutes) {
        List<AttendanceCardType> out = new ArrayList<>();

        /* WorkStatus 전략 실행 — true 면 조기 반환 */
        if (WS_STRATEGY.getOrDefault(r.getWorkStatus(), NO_OP).resolve(r, out)) return out;

        boolean hasCheckIn  = r.getCheckInAt()  != null;
        boolean hasCheckOut = r.getCheckOutAt() != null;

        /* VACATION_ATTEND — 오늘 승인 휴가 있는데 출근한 경우 */
        if (Boolean.TRUE.equals(r.getHasApprovedVacationToday()) && hasCheckIn)
            out.add(AttendanceCardType.VACATION_ATTEND);

        /* MISSING_COMMUTE
         *  (a) 근무예정일 + CommuteRecord 없음 (배치 전 실시간)
         *  (b) 체크인 있고 groupEndTime 지났는데 체크아웃 없음 */
        LocalTime nowT = LocalTime.now();
        if (isScheduledWorkDay(r.getGroupWorkDay(), date) && r.getComRecId() == null) {
            out.add(AttendanceCardType.MISSING_COMMUTE);
        } else if (hasCheckIn && !hasCheckOut
                && r.getGroupEndTime() != null
                && nowT.isAfter(r.getGroupEndTime())) {
            out.add(AttendanceCardType.MISSING_COMMUTE);
        }

        /* UNDER_MIN_HOUR — 체크아웃 완료만 판정. 휴게시간 무시 */
        if (hasCheckOut && r.getGroupStartTime() != null && r.getGroupEndTime() != null) {
            long workedMin    = Duration.between(r.getCheckInAt(), r.getCheckOutAt()).toMinutes();
            long scheduledMin = Duration.between(r.getGroupStartTime(), r.getGroupEndTime()).toMinutes();
            if (workedMin < scheduledMin) out.add(AttendanceCardType.UNDER_MIN_HOUR);
        }

        /* UNAPPROVED_OT — 정시 1분 초과 + 오늘 승인 OT 없음 */
        if (hasCheckOut && r.getGroupEndTime() != null
                && r.getCheckOutAt().toLocalTime().isAfter(r.getGroupEndTime())
                && (r.getApprovedOtMinutesToday() == null || r.getApprovedOtMinutesToday() == 0L))
            out.add(AttendanceCardType.UNAPPROVED_OT);

        /* MAX_HOUR_EXCEED */
        if (r.getWeekWorkedMinutes() != null && r.getWeekWorkedMinutes() > weeklyMaxMinutes)
            out.add(AttendanceCardType.MAX_HOUR_EXCEED);

        /* NORMAL — 체크인 있고 이상 상태 없으면 */
        if (hasCheckIn && out.isEmpty()) out.add(AttendanceCardType.NORMAL);

        return out;
    }

    /* groupWorkDay 비트마스크(월1, 화2, 수4, 목8, 금16, 토32, 일64) 기반 근무예정일 판정 */
    private boolean isScheduledWorkDay(Integer groupWorkDay, LocalDate date) {
        if (groupWorkDay == null) return false;
        int bit = 1 << (date.getDayOfWeek().getValue() - 1); // MONDAY=1 → bit0
        return (groupWorkDay & bit) != 0;
    }
}

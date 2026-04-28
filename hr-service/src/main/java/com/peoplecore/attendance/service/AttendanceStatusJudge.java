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
 *  - ABSENT(가상)    : 소정근무일 + 공휴일 X + 휴가 X + CommuteRecord 없음 + 근무종료 후
 *  - VACATION_ATTEND : 오늘 APPROVED 휴가 + 체크인 존재
 *  - MISSING_COMMUTE : (a) 근무예정일 + CommuteRecord 없음 + 근무시작 시각 경과 후
 *                       (b) 체크인 있고 퇴근시각 지나도 체크아웃 없음
 *  - UNDER_MIN_HOUR  : 체크아웃 완료 + 실근무분 < 소정근무분
 *  - UNAPPROVED_OT   : 체크아웃 > groupEndTime + 승인 OT 없음
 *  - MAX_HOUR_EXCEED : weekWorkedMinutes > weeklyMaxMinutes
 *  - NORMAL          : 체크인 있고 위 이상 상태 모두 해당 없음
 */
@Component
public class AttendanceStatusJudge {

    @FunctionalInterface
    private interface WorkStatusStrategy {
        boolean resolve(AttendanceAdminRow r, List<AttendanceCardType> out);
    }

    private static final WorkStatusStrategy NO_OP = (r, out) -> false;

    private static final EnumMap<WorkStatus, WorkStatusStrategy> WS_STRATEGY = new EnumMap<>(WorkStatus.class);
    static {
        WS_STRATEGY.put(WorkStatus.ABSENT, (r, out) -> {
            if (!Boolean.TRUE.equals(r.getHasApprovedVacationToday())) {
                out.add(AttendanceCardType.ABSENT);
                return true;
            }
            return false;
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
    }

    /* 카드 상태 배열 계산. isHoliday: 회사 공휴일 여부 (호출부에서 BusinessDayCalculator 캐시로 주입) */
    public List<AttendanceCardType> judge(AttendanceAdminRow r, LocalDate date,
                                          int weeklyMaxMinutes, boolean isHoliday) {
        List<AttendanceCardType> out = new ArrayList<>();

        if (WS_STRATEGY.getOrDefault(r.getWorkStatus(), NO_OP).resolve(r, out)) return out;

        boolean hasCheckIn  = r.getCheckInAt()  != null;
        boolean hasCheckOut = r.getCheckOutAt() != null;
        boolean scheduledWorkDay = isScheduledWorkDay(r.getGroupWorkDay(), date);
        boolean hasVacation = Boolean.TRUE.equals(r.getHasApprovedVacationToday());
        boolean recordMissing = r.getComRecId() == null;

        /* 가상 결근 — 배치 전이라도 결근 확정 케이스. AutoCloseBatchService.processAbsent 와 동일 조건 */
        if (recordMissing && scheduledWorkDay && !hasVacation && !isHoliday
                && isWorkdayOver(date, r.getGroupEndTime())) {
            out.add(AttendanceCardType.ABSENT);
            return out;
        }

        if (hasVacation && hasCheckIn) out.add(AttendanceCardType.VACATION_ATTEND);

        LocalTime nowT = LocalTime.now();
        /* (a) 근무예정일 + 레코드 없음 — groupStartTime 경과 후에만 잡음 (새벽 오탐 방지) */
        if (scheduledWorkDay && recordMissing
                && isWorkdayStarted(date, r.getGroupStartTime())) {
            out.add(AttendanceCardType.MISSING_COMMUTE);
        } else if (hasCheckIn && !hasCheckOut
                && r.getGroupEndTime() != null
                && nowT.isAfter(r.getGroupEndTime())) {
            /* (b) 체크인 있고 퇴근시각 경과인데 체크아웃 없음 */
            out.add(AttendanceCardType.MISSING_COMMUTE);
        }

        if (hasCheckOut && r.getGroupStartTime() != null && r.getGroupEndTime() != null) {
            long workedMin    = Duration.between(r.getCheckInAt(), r.getCheckOutAt()).toMinutes();
            long scheduledMin = Duration.between(r.getGroupStartTime(), r.getGroupEndTime()).toMinutes();
            if (workedMin < scheduledMin) out.add(AttendanceCardType.UNDER_MIN_HOUR);
        }

        if (hasCheckOut && r.getGroupEndTime() != null
                && r.getCheckOutAt().toLocalTime().isAfter(r.getGroupEndTime())
                && (r.getApprovedOtMinutesToday() == null || r.getApprovedOtMinutesToday() == 0L))
            out.add(AttendanceCardType.UNAPPROVED_OT);

        if (r.getWeekWorkedMinutes() != null && r.getWeekWorkedMinutes() > weeklyMaxMinutes)
            out.add(AttendanceCardType.MAX_HOUR_EXCEED);

        if (hasCheckIn && out.isEmpty()) out.add(AttendanceCardType.NORMAL);

        return out;
    }

    /* groupWorkDay 비트마스크(월1, 화2, 수4, 목8, 금16, 토32, 일64) 기반 근무예정일 판정 */
    private boolean isScheduledWorkDay(Integer groupWorkDay, LocalDate date) {
        if (groupWorkDay == null) return false;
        int bit = 1 << (date.getDayOfWeek().getValue() - 1);
        return (groupWorkDay & bit) != 0;
    }

    /* 결근 확정 시점 가드 — 과거면 true / 오늘이면 groupEndTime 경과 후 / 미래·groupEndTime null 이면 false */
    private boolean isWorkdayOver(LocalDate date, LocalTime groupEndTime) {
        LocalDate today = LocalDate.now();
        if (date.isBefore(today)) return true;
        if (date.isAfter(today)) return false;
        if (groupEndTime == null) return false;
        return LocalTime.now().isAfter(groupEndTime);
    }

    /* MISSING_COMMUTE(a) 시점 가드 — 과거면 true / 오늘이면 groupStartTime 경과 후 / 미래·null 이면 false */
    private boolean isWorkdayStarted(LocalDate date, LocalTime groupStartTime) {
        LocalDate today = LocalDate.now();
        if (date.isBefore(today)) return true;
        if (date.isAfter(today)) return false;
        if (groupStartTime == null) return false;
        return LocalTime.now().isAfter(groupStartTime);
    }
}

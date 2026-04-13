package com.peoplecore.attendance.service;

import com.peoplecore.attendance.dto.AttendanceAdminRow;
import com.peoplecore.attendance.entity.AttendanceCardType;
import com.peoplecore.attendance.entity.CheckInStatus;
import com.peoplecore.attendance.entity.CheckOutStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/*
 * AttendanceAdminRow 한 건을 입력받아 해당 사원이 가진 카드 상태(복수) 를 판정.
 *
 * 판정 규칙 (인계서 기준 + 사용자 확정):
 *  - WORKING         : 체크인 존재 (퇴근 여부 무관)
 *  - LATE            : CheckInStatus = LATE
 *  - EARLY_LEAVE     : CheckOutStatus = EARLY_LEAVE
 *  - VACATION_ATTEND : 오늘 APPROVED 휴가 + 체크인 존재
 *  - OFFSITE         : isOffsite = true
 *  - MISSING_COMMUTE : (근무예정일인데 체크인 없음) or (체크인 있고 groupEndTime 지났는데 체크아웃 없음)
 *  - UNDER_MIN_HOUR  : 체크아웃 완료 + 실근무분 < (groupEndTime - groupStartTime)
 *                      휴게시간은 계산 제외 (사용자 결정)
 *  - UNAPPROVED_OT   : 체크아웃 시각 > groupEndTime (1분이라도 초과) + approvedOtMinutesToday == 0
 *  - MAX_HOUR_EXCEED : weekWorkedMinutes > weeklyMaxMinutes
 *  - NORMAL          : 체크인 존재 + 위 이상 상태 모두 해당 없음 (WORKING 외 추가 상태 0개)
 */
@Component
public class AttendanceStatusJudge {

    /**
     * 한 행의 카드 상태 배열을 계산.
     */
    public List<AttendanceCardType> judge(AttendanceAdminRow r, LocalDate date, int weeklyMaxMinutes) {
        List<AttendanceCardType> out = new ArrayList<>();

        boolean hasCheckIn = r.getComRecId() != null;
        boolean hasCheckOut = r.getCheckOutAt() != null;

        // WORKING — 체크인 존재
        if (hasCheckIn) out.add(AttendanceCardType.WORKING);

        // LATE
        if (r.getCheckInStatus() == CheckInStatus.LATE) {
            out.add(AttendanceCardType.LATE);
        }

        // EARLY_LEAVE
        if (r.getCheckOutStatus() == CheckOutStatus.EARLY_LEAVE) {
            out.add(AttendanceCardType.EARLY_LEAVE);
        }

        // VACATION_ATTEND — 오늘 승인 휴가 있는데 출근한 경우
        if (Boolean.TRUE.equals(r.getHasApprovedVacationToday()) && hasCheckIn) {
            out.add(AttendanceCardType.VACATION_ATTEND);
        }

        // OFFSITE
        if (Boolean.TRUE.equals(r.getIsOffsite())) {
            out.add(AttendanceCardType.OFFSITE);
        }

        // MISSING_COMMUTE
        // (a) 근무예정일인데 체크인 없음
        // (b) 체크인 있고 groupEndTime 지났는데 체크아웃 없음
        boolean isScheduledWorkDay = isScheduledWorkDay(r.getGroupWorkDay(), date);
        LocalTime nowT = LocalTime.now();
        if (isScheduledWorkDay && !hasCheckIn) {
            out.add(AttendanceCardType.MISSING_COMMUTE);
        } else if (hasCheckIn && !hasCheckOut
                && r.getGroupEndTime() != null
                && nowT.isAfter(r.getGroupEndTime())) {
            out.add(AttendanceCardType.MISSING_COMMUTE);
        }

        // UNDER_MIN_HOUR — 체크아웃 완료만 판정. 휴게시간 무시.
        if (hasCheckOut && r.getGroupStartTime() != null && r.getGroupEndTime() != null) {
            long workedMin = Duration.between(r.getCheckInAt(), r.getCheckOutAt()).toMinutes();
            long scheduledMin = Duration.between(r.getGroupStartTime(), r.getGroupEndTime()).toMinutes();
            if (workedMin < scheduledMin) {
                out.add(AttendanceCardType.UNDER_MIN_HOUR);
            }
        }

        // UNAPPROVED_OT — 정시 1분이라도 초과 + 오늘 승인 OT 없음
        if (hasCheckOut && r.getGroupEndTime() != null
                && r.getCheckOutAt().toLocalTime().isAfter(r.getGroupEndTime())
                && (r.getApprovedOtMinutesToday() == null || r.getApprovedOtMinutesToday() == 0L)) {
            out.add(AttendanceCardType.UNAPPROVED_OT);
        }

        // MAX_HOUR_EXCEED
        if (r.getWeekWorkedMinutes() != null && r.getWeekWorkedMinutes() > weeklyMaxMinutes) {
            out.add(AttendanceCardType.MAX_HOUR_EXCEED);
        }

        // NORMAL — 체크인 있고 WORKING 외 이상 상태가 전혀 없으면
        if (hasCheckIn && out.size() == 1 && out.get(0) == AttendanceCardType.WORKING) {
            out.add(0, AttendanceCardType.NORMAL);
        }

        return out;
    }

    /**
     * groupWorkDay 비트마스크(월1, 화2, 수4, 목8, 금16, 토32, 일64) 기반 오늘 근무예정일 판정.
     * groupWorkDay 가 null(근무그룹 미배정) 이면 근무예정 아님 취급.
     */
    private boolean isScheduledWorkDay(Integer groupWorkDay, LocalDate date) {
        if (groupWorkDay == null) return false;
        int bit = 1 << (date.getDayOfWeek().getValue() - 1); // MONDAY=1 → bit0
        return (groupWorkDay & bit) != 0;
    }
}
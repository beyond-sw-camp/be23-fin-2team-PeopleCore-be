package com.peoplecore.attendance.dto;

import com.peoplecore.attendance.entity.HolidayReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 근태 정정 주간 그리드 Response.
 * GET /attendance/modify/week?weekStart=YYYY-MM-DD
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceModifyWeekResDto {

    /* 주 시작일 (월요일). 서버에서 요청값 정규화 */
    private LocalDate weekStart;

    /* 주 종료일 (일요일) */
    private LocalDate weekEnd;

    /* 7일 슬롯 — CommuteRecord 없는 날도 빈 값으로 포함 */
    private List<Day> days;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Day {
        /* 근무 일자 */
        private LocalDate workDate;

        /* 요일 (MON/TUE/.../SUN) */
        private DayOfWeek dayOfWeek;

        /* 휴일 여부 — holidayReason != null 또는 토/일 */
        private Boolean isHoliday;

        /* 휴일 사유 (NATIONAL/COMPANY/WEEKLY_OFF) — 평일이면 null */
        private HolidayReason holidayReason;

        /* CommuteRecord PK (없는 날 null) */
        private Long comRecId;

        /* 출근 시각 (nullable) */
        private LocalDateTime checkIn;

        /* 퇴근 시각 (nullable) */
        private LocalDateTime checkOut;

        /* 실근무 분 (휴게 차감 완료) */
        private Long actualWorkMinutes;

        /* 인정된 추가 근무분 */
        private Long recognizedOvertimeMinutes;

        /*
         * 미인증 초과 근무 분 = max(0, overtimeMinutes - recognizedExtendedMinutes).
         * 정정 후보 판단용 — 이 값이 > 0 이면 사원이 초과근무 승인 없이 더 일했다는 뜻.
         */
        private Long unrecognizedOvertimeMinutes;

        /**
         * 자동 마감 여부
         */
        private Boolean isAutoClosed;
    }
}
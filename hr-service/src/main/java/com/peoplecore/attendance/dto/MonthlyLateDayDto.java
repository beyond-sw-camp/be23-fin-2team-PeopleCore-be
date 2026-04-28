package com.peoplecore.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/* 월간 지각 발생일 1행 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyLateDayDto {

    /* 근무 일자 */
    private LocalDate workDate;

    /* 실제 출근 시각 (LATE/LATE_AND_EARLY 행이므로 항상 not-null) */
    private LocalDateTime checkInAt;

    /* 지각 분 — max(0, checkIn.time - groupStartTime) */
    private long lateMinutes;
}

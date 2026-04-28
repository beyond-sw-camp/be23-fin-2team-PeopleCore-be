package com.peoplecore.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/* 월간 초과근무 발생일 1행 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyOvertimeDayDto {

    /* 근무 일자 */
    private LocalDate workDate;

    /* 초과근무 시작 시각 — APPROVED OT.otPlanStart 우선, 없으면 (workDate + workGroup.groupEndTime) 폴백 */
    private LocalDateTime overtimeStartAt;

    /* 실제 퇴근 시각 */
    private LocalDateTime checkOutAt;

    /* 인증된 초과근무 분 = recognizedExtended + recognizedNight + recognizedHoliday */
    private long approvedOvertimeMinutes;
}

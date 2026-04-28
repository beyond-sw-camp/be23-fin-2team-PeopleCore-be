package com.peoplecore.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/* 사원 개인 월간 근태 요약 응답 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceMyMonthlySummaryResDto {

    /* 조회 대상 월 — "YYYY-MM" */
    private String yearMonth;

    /* 지각 일수 — workStatus IN ('LATE','LATE_AND_EARLY') 카운트 */
    private int lateCount;

    /* 인증된 초과근무 합계 (분) */
    private long overtimeMinutes;

    /* 초과근무 발생일 수 */
    private int overtimeDayCount;

    /* 지각 발생일 상세 */
    private List<MonthlyLateDayDto> lateDays;

    /* 초과근무 발생일 상세 */
    private List<MonthlyOvertimeDayDto> overtimeDays;
}

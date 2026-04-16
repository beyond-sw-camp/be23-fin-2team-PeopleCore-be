package com.peoplecore.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/*
 * 주간 개인 근태 집계.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyWeeklyStatsDto {

    /* 주 시작일 (월요일) */
    private LocalDate weekStart;

    /* 주 종료일 (일요일) */
    private LocalDate weekEnd;

    /* 주간 실 근무 분 (휴게시간 차감, 휴가 미포함, isAutoClosed 건 제외) */
    private Long workedMinutes;

    /* 출근 일수 (CommuteRecord 존재 + checkIn 있음) */
    private Integer attendedDays;

    /* 근무 요일 수 (WorkGroup.groupWorkDay 비트 카운트) */
    private Integer workDays;

    /* 잔여 근무일 = workDays - attendedDays (음수는 0) */
    private Integer remainingDays;

    /* 잔여 근로 분 = max(0, 주적정분 - workedMinutes - vacationMinutes) */
    private Long remainingMinutes;

    /* 인정 초과 분 (recognized_extended + night + holiday 합) */
    private Long approvedOvertimeMinutes;

    /* 주간 사용 휴가 분 (승인 휴가 구간과 이번 주의 교집합 근무일 환산) */
    private Long vacationMinutes;

    /* 자동마감(isAutoClosed=true) 일수 — UI 에 "근태 이상 N건" 경고 표시용 */
    private Integer abnormalDays;
}
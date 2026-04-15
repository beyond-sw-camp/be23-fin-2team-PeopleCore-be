package com.peoplecore.attendance.dto;

/*
 * 주간요약 통합 집계 결과 — QueryDSL Projections.constructor 매핑 대상.
 * 모두 분(minute) 또는 일수 단위. NULL 방지 위해 SQL 단계에서 COALESCE 처리됨.
 */
public record WeeklyCommuteAggregate(
        Long workedMinutes,        // 자동마감/미체크아웃 제외 실근무 분 합
        Long attendedDays,         // checkIn 있는 distinct workDate 카운트
        Long recognizedMinutes,    // recognized_extended + night + holiday 합
        Long autoClosedDays        // is_auto_closed = TRUE 카운트
) {}
package com.peoplecore.attendance.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/*
 * 주간요약 휴가 계산용 슬라이스 — VacationRequest  엔티티에서 필요 컬럼만 추출.
 *  - startAt, endAt: 휴가 구간 (시각 포함, 주 교집합 계산용)
 *  - useDay: 휴가 사용 일수 (반차 0.5, 반반차 0.25, 종일 1.0+)
 */
public record VacationSlice(
        LocalDateTime startAt,
        LocalDateTime endAt,
        BigDecimal useDay
) {}
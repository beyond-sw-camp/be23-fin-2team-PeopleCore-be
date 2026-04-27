package com.peoplecore.attendance.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/*
 * 주간/월간 근태 화면용 휴가 슬라이스 — VacationRequest 에서 필요 컬럼만 추출.
 *  - startAt, endAt: 휴가 구간 (시각 포함, 주 교집합 계산용)
 *  - useDay: 휴가 사용 일수 (반차 0.5, 반반차 0.25, 종일 1.0+)
 *  - typeName: 휴가 유형명 (그리드 표기용 — "연차", "오전 반차" 등). 분 계산 호출부는 무시 가능.
 */
public record VacationSlice(
        LocalDateTime startAt,
        LocalDateTime endAt,
        BigDecimal useDay,
        String typeName
) {}
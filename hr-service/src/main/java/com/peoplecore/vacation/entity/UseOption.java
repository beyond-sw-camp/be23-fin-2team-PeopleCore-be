package com.peoplecore.vacation.entity;

import java.math.BigDecimal;

/* 휴가 사용 단위 옵션 - 신청 시 선택한 차감 단위 분류 */
/* 관리자 조회 화면에서 "반차/종일/반반차" 표시용. DB 컬럼은 아니고 requestUseDays 소수부 기반 파생 */
public enum UseOption {

    /* 종일 - useDays 소수부 0 (예: 1.0, 3.0) */
    FULL_DAY,
    /* 반차 - useDays 소수부 0.5 (예: 0.5, 2.5) */
    HALF_DAY,
    /* 반반차 - useDays 소수부 0.25 / 0.75 (예: 0.25, 1.75) */
    QUARTER_DAY;

    /* useDays 값에서 사용 단위 판정 - 소수부를 100배해 나머지로 분류 */
    /* 예외: days null 또는 음수면 IllegalArgumentException (정상 흐름상 도달 불가) */
    public static UseOption fromDays(BigDecimal days) {
        if (days == null || days.signum() <= 0) {
            throw new IllegalArgumentException("useDays 는 양수여야 함 - days=" + days);
        }
        // 소수부 추출 (abs 필요 없음 - 양수 보장) → 0.25 단위 환산
        BigDecimal fractional = days.remainder(BigDecimal.ONE);
        // 0.5 → HALF, 0.25 or 0.75 → QUARTER, 0 → FULL
        int hundredths = fractional.multiply(BigDecimal.valueOf(100)).intValueExact();
        return switch (hundredths) {
            case 0  -> FULL_DAY;
            case 50 -> HALF_DAY;
            case 25, 75 -> QUARTER_DAY;
            // 0.125(1시간) 등 분 단위 세부 지원 시 기본 QUARTER_DAY 로 묶음
            default -> QUARTER_DAY;
        };
    }
}

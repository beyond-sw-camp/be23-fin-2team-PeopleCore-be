package com.peoplecore.vacation.util;

import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;

import java.math.BigDecimal;

/* 유산·사산휴가 법정 일수 산정 규칙 - 근기법 §74⑦ 및 시행령 기준 */
/* 상태 패턴 (enum per-bucket 행동 캡슐화) - 각 구간이 matches/days 를 가짐 */
/*   ≤ 11주 : 5일  /  12~15주: 10일  /  16~21주: 30일  /  22~27주: 60일  /  ≥ 28주: 90일 */
/* 법 개정 시 구간만 추가/수정 → 호출부 변경 없음 */
public enum MiscarriageLeaveRule {

    /* 11주 이하 유산 - 5일 */
    WEEKS_UNDER_12(5) {
        @Override public boolean matches(int weeks) { return weeks <= 11; }
    },
    /* 12~15주 유산 - 10일 */
    WEEKS_12_TO_15(10) {
        @Override public boolean matches(int weeks) { return weeks >= 12 && weeks <= 15; }
    },
    /* 16~21주 유산 - 30일 */
    WEEKS_16_TO_21(30) {
        @Override public boolean matches(int weeks) { return weeks >= 16 && weeks <= 21; }
    },
    /* 22~27주 유산 - 60일 */
    WEEKS_22_TO_27(60) {
        @Override public boolean matches(int weeks) { return weeks >= 22 && weeks <= 27; }
    },
    /* 28주 이상 사산 - 90일 (법정 최대) */
    WEEKS_28_PLUS(90) {
        @Override public boolean matches(int weeks) { return weeks >= 28; }
    };

    /* 해당 구간 법정 휴가 일수 */
    private final int days;

    MiscarriageLeaveRule(int days) { this.days = days; }

    /* 주수가 이 구간에 속하는지 판정 - 각 상수가 override */
    public abstract boolean matches(int weeks);

    /* 해당 구간 일수 - VacationBalance.accrue 입력 타입 정합 위해 BigDecimal */
    public BigDecimal days() { return BigDecimal.valueOf(days); }

    /* 주수 → 법정 일수 변환 진입점 */
    /* 예외: weeks null/1 미만 시 VACATION_REQ_PREGNANCY_WEEKS_INVALID */
    /*       구간 매칭 실패(정상 흐름상 도달 불가) 시 IllegalStateException */
    public static BigDecimal daysForWeeks(Integer weeks) {
        if (weeks == null || weeks < 1) {
            throw new CustomException(ErrorCode.VACATION_REQ_PREGNANCY_WEEKS_INVALID);
        }
        for (MiscarriageLeaveRule rule : values()) {
            if (rule.matches(weeks)) return rule.days();
        }
        // values() 는 1..∞ 를 전부 덮음 (WEEKS_28_PLUS 가 catch-all). 이 예외는 방어 코드
        throw new IllegalStateException("매칭되는 유산사산 주수 구간 없음 - weeks=" + weeks);
    }
}

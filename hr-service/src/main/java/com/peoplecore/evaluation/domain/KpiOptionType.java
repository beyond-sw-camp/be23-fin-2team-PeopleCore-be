package com.peoplecore.evaluation.domain;

// KPI 옵션 종류
public enum KpiOptionType {
    DIRECTION,   // 방향 (UP/DOWN/MAINTAIN)
    UNIT,        // 단위 (PERCENT/COUNT/WON/HOUR/SCORE/DAY)
    CATEGORY,    // 카테고리 (업무성과/역량개발/조직기여)
    DEPARTMENT   // 부서 (COMMON/영업팀/...)
}

package com.peoplecore.evaluation.domain;

// 등급 목록 정렬 기준 (자동산정/보정/결과 공통)
public enum EvalGradeSortField {
    EMP_NUM,       // 사번
    EMP_NAME,      // 이름
    DEPT_NAME,     // 부서
    TOTAL_SCORE,   // 종합점수
    AUTO_GRADE,    // 예정등급 (불변)
    FINAL_GRADE    // 확정/보정 (최신 보정값)
}

package com.peoplecore.evaluation.domain;

// KPI 옵션 종류
//   CATEGORY/UNIT : 회사별 리스트 관리 (관리자가 자유 추가/수정/삭제)
//   DEPARTMENT    : 부서 depth 설정 (회사당 1행, option_value 에 "1".."N" / "leaf" 저장)
public enum KpiOptionType {
    CATEGORY,
    UNIT,
    DEPARTMENT
}

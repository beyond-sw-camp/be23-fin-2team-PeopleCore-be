package com.peoplecore.evaluation.domain;

// 단계의 시스템적 의미 — 이름은 바뀔 수 있어도 이 타입이 로직 식별자
public enum StageType {
    GOAL_ENTRY,     // 목표등록
    EVALUATION,     // 평가 항목들 (자기/상위자/커스텀 — 여러 개 가능)
    GRADING,        // 등급 산정 및 보정
    FINALIZATION    // 결과확정
}

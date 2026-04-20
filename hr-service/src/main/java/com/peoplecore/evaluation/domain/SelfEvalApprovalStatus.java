package com.peoplecore.evaluation.domain;

// 자기평가 승인 상태 (팀장이 사원 자기평가를 검토, 프론트가 한글 라벨 매핑)
public enum SelfEvalApprovalStatus {
    DRAFT,     // 작성중 (미제출)
    PENDING,   // 대기 (제출됨, 팀장 판단 전)
    APPROVED,  // 승인
    REJECTED   // 반려
}

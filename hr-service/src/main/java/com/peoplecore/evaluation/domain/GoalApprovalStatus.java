package com.peoplecore.evaluation.domain;

// 목표 승인 상태 (팀장이 사원 목표를 검토, 프론트가 한글 라벨 매핑)
public enum GoalApprovalStatus {
    PENDING,   // 대기
    APPROVED,  // 승인
    REJECTED   // 반려
}

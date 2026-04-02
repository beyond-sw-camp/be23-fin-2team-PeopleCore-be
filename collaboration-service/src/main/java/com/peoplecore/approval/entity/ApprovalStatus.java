package com.peoplecore.approval.entity;

public enum ApprovalStatus {
    DRAFT, // 임시 저장
    PENDING, // 결재 진행 중
    APPROVED, // 승인
    REJECTED, // 반려
    CANCELED // 취소
}

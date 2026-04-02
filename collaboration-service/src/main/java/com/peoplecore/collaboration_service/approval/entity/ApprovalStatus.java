package com.peoplecore.collaboration_service.approval.entity;

public enum ApprovalStatus {
    DRAFT(new DraftState()), // 임시 저장
    PENDING(new PendingState()), // 결재 진행 중
    APPROVED(new ApprovedState()), // 승인
    REJECTED(new RejectedState()), // 반려
    CANCELED(new CanceledState()); // 취소

    private final ApprovalState state;

    ApprovalStatus(ApprovalState state) {
        this.state = state;
    }

    public ApprovalState getState() {
        return state;
    }
}

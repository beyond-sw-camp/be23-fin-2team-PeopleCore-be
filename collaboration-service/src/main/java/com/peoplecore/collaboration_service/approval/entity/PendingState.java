package com.peoplecore.collaboration_service.approval.entity;

import com.peoplecore.common.exception.BusinessException;

public class PendingState implements ApprovalState {
    @Override
    public void approve(ApprovalDocument document) {
        document.changeStatus(ApprovalStatus.APPROVED);
        document.complete();
    }

    @Override
    public void reject(ApprovalDocument document) {
        document.changeStatus(ApprovalStatus.REJECTED);
        document.complete();
    }

    @Override
    public void recall(ApprovalDocument document) {
        document.changeStatus(ApprovalStatus.CANCELED);
        document.complete();
    }

    @Override
    public void submit(ApprovalDocument document) {
        throw new BusinessException("이미 결재 진행 중인 문서입니다.");
    }
}

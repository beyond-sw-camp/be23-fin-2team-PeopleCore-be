package com.peoplecore.approval.entity;

import com.peoplecore.exception.BusinessException;

public class DraftState implements ApprovalState {


    @Override
    public void approve(ApprovalDocument document) {
        throw new BusinessException("임시저장 문서는 승인할 수 없습니다.");
    }

    @Override
    public void reject(ApprovalDocument document) {
        throw new BusinessException("임시저장 문서는 반려할 수 없습니다");
    }

    @Override
    public void recall(ApprovalDocument document) {
        throw new BusinessException("임시저장 문서는 회수할 수 없습니다.");
    }

    @Override
    public void submit(ApprovalDocument document) {
        document.changeStatus(ApprovalStatus.PENDING);
        document.markSubmitted();
    }
}

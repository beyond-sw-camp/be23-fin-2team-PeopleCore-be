package com.peoplecore.approval.entity;

import com.peoplecore.common.exception.BusinessException;

public class RejectedState implements ApprovalState {

    @Override
    public void approve(ApprovalDocument document) {
        throw new BusinessException("반려된 문서는 승인할 수 없습니다.");
    }

    @Override
    public void reject(ApprovalDocument document) {
        throw new BusinessException("이미 반려된 문서입니다.");
    }

    @Override
    public void recall(ApprovalDocument document) {
        throw new BusinessException("반려된 문서는 회수할 수 없습니다.");
    }

    @Override
    public void submit(ApprovalDocument document) {
        throw new BusinessException("반려된 문서는 재제출할 수 없습니다.");
    }
}
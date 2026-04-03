package com.peoplecore.approval.entity;

import com.peoplecore.exception.BusinessException;

public class CanceledState implements ApprovalState {

    @Override
    public void approve(ApprovalDocument document) {
        throw new BusinessException("회수된 문서는 승인할 수 없습니다.");
    }

    @Override
    public void reject(ApprovalDocument document) {
        throw new BusinessException("회수된 문서는 반려할 수 없습니다.");
    }

    @Override
    public void recall(ApprovalDocument document) {
        throw new BusinessException("이미 회수된 문서입니다.");
    }

    @Override
    public void submit(ApprovalDocument document) {
        throw new BusinessException("회수된 문서는 재제출할 수 없습니다.");
    }
}
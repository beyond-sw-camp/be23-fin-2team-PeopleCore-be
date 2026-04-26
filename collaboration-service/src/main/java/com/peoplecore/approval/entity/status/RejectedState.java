package com.peoplecore.approval.entity.status;

import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.approval.entity.ApprovalStatus;
import com.peoplecore.exception.BusinessException;

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
        document.changeStatus(ApprovalStatus.PENDING);
        document.markSubmitted();
    }

    /* REJECTED 만 재기안 허용 — default throw 를 무력화 */
    @Override
    public void ensureResubmittable() {
        /* REJECTED 는 통과 — 어떤 검증도 필요 없음 */
    }
}
package com.peoplecore.approval.entity.status;

import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.approval.entity.ApprovalStatus;
import com.peoplecore.exception.BusinessException;

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

    /* PENDING 만 결재 처리/회수 진입 허용 — default throw 를 무력화 */
    @Override
    public void ensureOpenForApproval() {
        /* PENDING 은 통과 — 어떤 검증도 필요 없음 */
    }
}

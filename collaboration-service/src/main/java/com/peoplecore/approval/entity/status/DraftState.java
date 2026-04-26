package com.peoplecore.approval.entity.status;

import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.approval.entity.ApprovalStatus;
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

    /* DRAFT 만 임시저장 단계 작업 허용 — default throw 를 무력화 */
    @Override
    public void ensureDraftStage() {
        /* DRAFT 는 통과 — 어떤 검증도 필요 없음 */
    }
}

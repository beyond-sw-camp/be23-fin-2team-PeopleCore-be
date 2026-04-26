package com.peoplecore.approval.entity.status;

import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.exception.BusinessException;

public interface ApprovalState {
    //    현재 상태에서 승인 가능한지
    void approve(ApprovalDocument document);

    //    현재 상태에서 반려 가능한지
    void reject(ApprovalDocument document);

    //    현재 상태에서 회수 가능한지
    void recall(ApprovalDocument document);

    //    현재 상태에서 제출 가능한지
    void submit(ApprovalDocument  document);

    /* 결재 처리(승인/반려/회수) 가능 여부 — PENDING 만 통과 */
    default void ensureOpenForApproval() {
        throw new BusinessException("결재 진행 중인 문서만 처리할 수 있습니다.");
    }

    /* 재기안 가능 여부 — REJECTED 만 통과 */
    default void ensureResubmittable() {
        throw new BusinessException("반려된 문서만 재기안할 수 있습니다.");
    }

    /* 임시저장 단계 작업(수정/삭제/정식 상신) 가능 여부 — DRAFT 만 통과 */
    default void ensureDraftStage() {
        throw new BusinessException("임시 저장 문서만 수정/삭제/상신할 수 있습니다.");
    }
}

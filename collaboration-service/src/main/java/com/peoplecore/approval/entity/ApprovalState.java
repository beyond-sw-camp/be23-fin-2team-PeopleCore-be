package com.peoplecore.approval.entity;

public interface ApprovalState {
    //    현재 상태에서 승인 가능한지
    void approve(ApprovalDocument document);

    //    현재 상태에서 반려 가능한지
    void reject(ApprovalDocument document);

    //    현재 상태에서 회수 가능한지
    void recall(ApprovalDocument document);

    //    현재 상태에서 제출 가능한지
    void submit(ApprovalDocument document);

}

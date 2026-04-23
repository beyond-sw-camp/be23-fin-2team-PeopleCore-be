package com.peoplecore.approval.entity;

public enum ApprovalLineStatus {
    PENDING,   // 대기
    APPROVED,  // 승인
    REJECTED,  // 반려 (본인이 직접 반려)
    DELEGATED, // 전결
    CANCELED   // 취소 (기안자 회수 or 앞 결재자 반려로 내 차례가 오지 않음)
}

package com.peoplecore.attendance.entity;

public enum OtStatus {
    /** 승인 대기 — collab 결재 문서 생성 직후 hr 로 insert 되는 초기 상태 */
    PENDING,
    /** 승인 */
    APPROVED,
    /** 반려 */
    REJECTED,
    /** 취소 — 기안자 회수(recall) 또는 관리자 직접 취소 */
    CANCELED
}

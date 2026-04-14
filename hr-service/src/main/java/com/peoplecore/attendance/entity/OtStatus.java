package com.peoplecore.attendance.entity;

public enum OtStatus {
    /** 임시 — hr 모달 "확인" 만 누르고 결재요청 전. 잔여 계산에서 제외. 2시간 후 스케줄러가 정리 */
    DRAFT,
    /** 승인 대기 — collab 결재 문서 생성됨 */
    PENDING,
    /** 승인 */
    APPROVED,
    /** 반려 */
    REJECTED,
    /** 취소 */
    CANCELED
}

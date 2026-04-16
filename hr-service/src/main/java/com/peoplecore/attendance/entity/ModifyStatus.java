package com.peoplecore.attendance.entity;

import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import lombok.Getter;

/**
 * 근태 정정 요청 처리 상태.
 * AttendenceModify.attenStatus 필드에서 사용.
 * <p>전이: PENDING → APPROVED | REJECTED | CANCELED
 */
@Getter
public enum ModifyStatus {
    /** 대기 */
    PENDING("대기"),
    /** 승인 */
    APPROVED("승인"),
    /** 반려 */
    REJECTED("반려"),
    /** 취소 (신청자 본인 철회) */
    CANCELED("취소");

    private final String label;

    ModifyStatus(String label) {
        this.label = label;
    }

    /**
     * 상태 전이 검증.
     * PENDING 에서만 다른 상태로 전이 가능.
     * 이미 처리된 요청을 다시 처리하려 할 경우 예외.
     */
    public void validateTransitionTo(ModifyStatus target) {
        if (this != PENDING) {
            throw new CustomException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
    }
}
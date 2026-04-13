package com.peoplecore.attendence.entity;

import lombok.Getter;

/**
 * 근태 레코드 상태.
 * Attendance.attenStatus 필드에서 사용.
 * <p>전이: CONFIRMED → MODIFIED (정정 승인 시)
 */
@Getter
public enum AttendanceStatus {
    /** 확정 (최초 집계 마감) */
    CONFIRMED("확정"),
    /** 수정됨 (정정 요청 승인 반영) */
    MODIFIED("수정");

    private final String label;

    AttendanceStatus(String label) {
        this.label = label;
    }
}
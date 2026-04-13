package com.peoplecore.attendence.entity;

import lombok.Getter;

/**
 * 근무 유형.
 * Attendance.attenWorkType 필드에서 사용.
 */
@Getter
public enum AttendanceWorkType {
    /** 정상 근무 */
    NORMAL("정상"),
    /** 휴가 (연차·반차·경조사 등) */
    VACATION("휴가"),
    /** 공휴일·주말 */
    HOLIDAY("휴일"),
    /** 휴일 대체 근무 */
    HOLIDAY_SUBSTITUTE("휴일대체"),
    /** 출장 */
    BUSINESS_TRIP("출장"),
    /** 재택 */
    REMOTE("재택"),
    /** 외근 */
    OUTSIDE("외근");

    private final String label;

    AttendanceWorkType(String label) {
        this.label = label;
    }
}
package com.peoplecore.attendance.entity;

import lombok.Getter;

/**
 * 휴일 이유 (CheckInStatus 와 직교).
 * HOLIDAY_WORK 로 판정됐을 때 근거 기록. null 이면 평일 근무일.
 */
@Getter
public enum HolidayReason {
    /** 법정공휴일 (Holidays.holidayType = NATIONAL, 전역) */
    NATIONAL("법정공휴일"),
    /** 사내일정 (Holidays.holidayType = COMPANY, 회사별) */
    COMPANY("사내휴일"),
    /** 근무그룹 기준 비근무요일 (주말 등) */
    WEEKLY_OFF("비근무요일");

    private final String label;

    HolidayReason(String label) {
        this.label = label;
    }
}
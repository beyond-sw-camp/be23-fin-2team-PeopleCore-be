package com.peoplecore.attendance.entity;

import lombok.Getter;

import java.time.LocalTime;

/**
 * 출근 체크인 상태.
 * 근무그룹 출근시각 초과 시 LATE, 그 외 ON_TIME. 휴일이면 HOLIDAY_WORK.
 */
@Getter
public enum CheckInStatus {
    /** 정시 이내 출근 (groupStartTime 이하) */
    ON_TIME("정시출근"),
    /** 지각 (groupStartTime 초과) */
    LATE("지각"),
    /** 휴일 출근 (공휴일/사내휴일/비근무요일) */
    HOLIDAY_WORK("휴일출근");

    private final String label;

    CheckInStatus(String label) {
        this.label = label;
    }

    /** 평일 근무일 전제. groupStart 와 동일 시각은 정시 인정 */
    public static CheckInStatus classifyWorkingDay(LocalTime checkInTime, LocalTime groupStart) {
        return checkInTime.isAfter(groupStart) ? LATE : ON_TIME;
    }
}
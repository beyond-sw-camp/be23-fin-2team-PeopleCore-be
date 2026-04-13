package com.peoplecore.attendance.entity;

import lombok.Getter;

import java.time.LocalTime;

/**
 * 퇴근 체크아웃 상태.
 * 근무그룹 퇴근시각 이전 퇴근 시 EARLY_LEAVE, 그 외 ON_TIME.
 */
@Getter
public enum CheckOutStatus {
    /** 조퇴 (groupEndTime 미만) */
    EARLY_LEAVE("조퇴"),
    /** 정시 이후 퇴근 (groupEndTime 이상) */
    ON_TIME("정시퇴근"),
    /** 휴일 퇴근 */
    HOLIDAY_WORK_END("휴일퇴근");

    private final String label;

    CheckOutStatus(String label) {
        this.label = label;
    }

    /** 평일 근무일 전제. groupEnd 동일 시각은 정시 인정 */
    public static CheckOutStatus classifyWorkingDay(LocalTime checkOutTime, LocalTime groupEnd) {
        return checkOutTime.isBefore(groupEnd) ? EARLY_LEAVE : ON_TIME;
    }
}
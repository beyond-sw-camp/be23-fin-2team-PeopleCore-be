package com.peoplecore.attendance.entity;

/*
 * 주간 근무시간 상태.
 * 판정 기준: OvertimePolicy 의 최대/경고 분값과 실 근무분 비교.
 *  - NORMAL:   worked < warning
 *  - WARNING:  warning <= worked <= max
 *  - EXCEEDED: worked > max
 */
public enum WeeklyWorkStatus {
    NORMAL,
    WARNING,
    EXCEEDED;

    /*
     * 주간 실근무 분 + 정책값으로 상태 판정.
     * 서비스 여러 곳에서 중복 판정하지 않도록 여기 모음.
     */
    public static WeeklyWorkStatus of(long workedMinutes, int maxMinute, int warningMinute) {
        if (workedMinutes > maxMinute) return EXCEEDED;
        if (workedMinutes >= warningMinute) return WARNING;
        return NORMAL;
    }
}
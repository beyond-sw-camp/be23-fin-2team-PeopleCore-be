package com.peoplecore.attendance.entity;

import lombok.Getter;

/**
 * 하루 단위 최종 근태 상태.
 * 체크인 시 초기 설정 → 체크아웃 시 확정 → 배치 시 AUTO_CLOSED/ABSENT 처리.
 */
@Getter
public enum WorkStatus {

    /* 정시 출퇴근 완료 */
    NORMAL("정상"),

    /* 정시보다 늦게 출근, 정시 이후 퇴근 */
    LATE("지각"),

    /* 정시 출근, 정시 이전 퇴근 */
    EARLY_LEAVE("조퇴"),

    /* 지각 출근 + 정시 이전 퇴근 */
    LATE_AND_EARLY("지각+조퇴"),

    /* 비근무일(주말/공휴일) 출근 */
    HOLIDAY_WORK("휴일근무"),

    /* 체크인 후 퇴근 미체크 → 배치 강제 마감 */
    AUTO_CLOSED("자동마감"),

    /* 근무예정일 또는 휴일근무 약속일에 미출근 */
    ABSENT("결근");

    private final String label;

    WorkStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

package com.peoplecore.attendance.entity;

import lombok.Getter;

/**
 * 근태 현황 대시보드 "일자별" 탭의 집계 카드 종류.
 * 한 사원이 여러 상태를 동시에 가질 수 있으므로 집계 시 중복 카운트 허용.
 */
@Getter
public enum AttendanceCardType {

    /* 정상 — 체크인 있고 이상 상태 없음 */
    NORMAL("정상"),

    /* 지각 — WorkStatus = LATE 또는 LATE_AND_EARLY */
    LATE("지각"),

    /* 조퇴 — WorkStatus = EARLY_LEAVE 또는 LATE_AND_EARLY */
    EARLY_LEAVE("조퇴"),

    /* 휴가 중 출근 — 오늘 APPROVED 휴가 + 체크인 존재 */
    VACATION_ATTEND("휴가 중 출근"),

    /* 출퇴근 누락 — 근무예정일인데 체크인 없음(배치 전), 또는 퇴근 시각 지났는데 체크아웃 없음 */
    MISSING_COMMUTE("출퇴근 누락"),

    /* 1일 소정근로 미달 — 체크아웃 완료 + 실제 근무분 < 소정 근로분 */
    UNDER_MIN_HOUR("1일 소정근로 미달"),

    /* 미승인 초과근무 — 정시 1분이라도 초과 퇴근 + 해당일 APPROVED OT 없음 */
    UNAPPROVED_OT("미승인 초과근무"),

    /* 주간 최대근무시간 초과 — 사원 주간 누적 > OvertimePolicy.otPolicyWeeklyMaxMinutes */
    MAX_HOUR_EXCEED("최대근무시간 초과"),

    /* 결근 — WorkStatus = ABSENT (배치가 삽입). 승인 휴가 있으면 Judge 에서 제외 */
    ABSENT("결근");

    private final String label;

    AttendanceCardType(String label) {
        this.label = label;
    }

    /** 프론트 UI 라벨 */
    public String getLabel() {
        return label;
    }
}
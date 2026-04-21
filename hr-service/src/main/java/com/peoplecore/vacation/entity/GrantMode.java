package com.peoplecore.vacation.entity;

/* 휴가 부여 방식 - 휴가 유형별 적립 타이밍 구분 */
/* SCHEDULED: 스케줄러 자동 적립 (연차/월차/생리)                        */
/* EVENT_BASED: 사용자가 신청할 때 Balance 생성 (출산/배우자출산/         */
/*              유산사산/가족돌봄/공가 - 증빙·사유 필요)                   */
public enum GrantMode {
    /* 스케줄러 자동 적립 - 회기 시작·매월 1일·매일 등 정해진 주기 */
    SCHEDULED,
    /* 사용자 신청 시 Balance 생성 - 승인·반려 결과에 따라 확정/취소 */
    EVENT_BASED
}

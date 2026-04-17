package com.peoplecore.vacation.entity;

/* 잔여 변동 이벤트 타입 - VacationLedger.eventType 컬럼 */
public enum LedgerEventType {

    /* 월차 매월 자동 적립 (스케줄러 → SCHEDULER ref) */
    ACCRUAL(true),

    /* 연차 회기 발생 (스케줄러 → SCHEDULER ref) */
    INITIAL_GRANT(true),

    /* 관리자 수동 부여 (관리자 → ADMIN ref) */
    MANUAL_GRANT(true),

    /* 결재 승인으로 차감 (Kafka APPROVED → VAC_REQUEST ref) */
    USED(false),

    /* 승인 후 취소로 잔여 복원 (사원/관리자 취소 → VAC_REQUEST ref) */
    RESTORED(true),

    /* 만료 소멸 (스케줄러 → SCHEDULER ref) */
    EXPIRED(false),

    /* 1년 도달 월차→연차 전환 (스케줄러 → SCHEDULER ref) */
    ANNUAL_TRANSITION(false);

    /* true = 잔여 증가(+), false = 잔여 감소(-). change_days 부호 검증/UI 색상 분기에 사용 */
    private final boolean credit;

    LedgerEventType(boolean credit) { this.credit = credit; }

    public boolean isCredit() { return credit; }
    public boolean isDebit()  { return !credit; }
}
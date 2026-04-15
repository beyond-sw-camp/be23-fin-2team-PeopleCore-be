package com.peoplecore.pay.enums;

public enum PayrollStatus {
    CALCULATING,   // 산정중
    CONFIRMED,     // 확정
    PENDING_APPROVAL,   //전자결재 승인전
    APPROVED,      // 승인완료
    PAID           // 지급완료
}

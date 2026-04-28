package com.peoplecore.pay.enums;

public enum PayrollEmpStatusType {
    CALCULATING,    // 산정중 (기본)
    CONFIRMED,       // 확정
    APPROVED,            // 결재 승인됨 (지급 가능)
    PAID                 // 지급 완료
}

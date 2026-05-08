package com.peoplecore.pay.enums;

public enum AllowanceStatus {   //수당 상태
    PENDING,
    CALCULATED,
    EXEMPTED,
    APPLIED,   //미산정, 산정완료, 확정완료, 급여반영
    SKIPPED    // 반영 시도했으나 급여대장 잠금(사원별 확정/결재중/승인/지급완료)으로 반영 불가
}

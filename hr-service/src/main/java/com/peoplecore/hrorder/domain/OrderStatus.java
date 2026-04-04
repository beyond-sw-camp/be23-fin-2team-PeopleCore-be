package com.peoplecore.hrorder.domain;

public enum OrderStatus {
    REGISTERED,    // 등록
    IN_APPROVAL,   // 결재중
    CONFIRMED,     // 확정
    REJECTED       // 반려
}

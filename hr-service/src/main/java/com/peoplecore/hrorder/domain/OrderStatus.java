package com.peoplecore.hrorder.domain;

public enum OrderStatus {
    SCHEDULED,  // 발령예정 (등록 완료, 발령일 도래 대기)
    APPLIED     // 발령완료 (스케줄러가 employee 반영 완료)
}

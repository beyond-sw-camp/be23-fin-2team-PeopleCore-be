package com.peoplecore.pay.enums;

public enum DepStatus {
    SCHEDULED,   // 적립예정 - 급여확정후 지급처리전
    COMPLETED,   // 적립완료 - 지급처리 완료시점 자동생성 or 수동등록
    CANCELED     // 취소
}

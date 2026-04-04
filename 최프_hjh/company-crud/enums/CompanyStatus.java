package com.peoplecore.enums;

public enum CompanyStatus {
    PENDING,    // 회사 등록 직후 (계약 확정 전)
    ACTIVE,     // 계약 활성 상태 (서비스 이용 가능)
    SUSPENDED,  // 일시 중지 (관리자가 수동으로 중지)
    EXPIRED     // 계약 만료 (자동 전환 or 수동)
}

package com.peoplecore.pay.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 급여 관련 Kafka 이벤트 모델
 * - 급여 산정 완료, 명세서 발급, 퇴직금 산정 시 발행
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalaryEvent {

    private UUID companyId;
    private String eventType;        // PAY_STUB_CREATED, PAYROLL_CONFIRMED, PENSION_UPDATED
    private String payYearMonth;     // 해당 급여월
    private List<Long> empIds;       // 대상 사원 ID 목록
    private String message;          // 알림 메시지

    // 이벤트 타입 상수
    public static final String PAY_STUB_CREATED = "PAY_STUB_CREATED";
    public static final String PAYROLL_CONFIRMED = "PAYROLL_CONFIRMED";
    public static final String PENSION_UPDATED = "PENSION_UPDATED";
    public static final String SALARY_INFO_UPDATED = "SALARY_INFO_UPDATED";
}

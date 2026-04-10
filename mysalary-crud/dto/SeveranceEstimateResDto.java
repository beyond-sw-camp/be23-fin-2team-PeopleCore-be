package com.peoplecore.pay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 예상 퇴직금 산정 결과 응답 DTO
 * 근속기준 퇴직금 예상액
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeveranceEstimateResDto {

    private LocalDate hireDate;              // 입사일
    private LocalDate resignDate;            // 예상 퇴사일
    private Boolean hasMidSettlement;        // 퇴직금 중간정산 여부
    private String settlementPeriod;         // 퇴직 정산기간
    private Long serviceDays;               // 근속일수
    private Integer last3MonthTotalDays;     // 예상 퇴직일 이전 3개월 총 일수
    private Long last3MonthTotalPay;         // 최근 3개월 급여 총액
    private Long lastYearBonusTotal;         // 직전 1년간 상여금 총액
    private Long annualLeaveAllowance;       // 연차수당
    private BigDecimal avgDailyWage;         // 1일 평균임금
    private Long estimatedSeverance;         // 예상 퇴직금
}

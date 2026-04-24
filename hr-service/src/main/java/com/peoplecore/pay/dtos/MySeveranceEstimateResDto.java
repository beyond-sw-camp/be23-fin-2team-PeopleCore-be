package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MySeveranceEstimateResDto {

    // ── 사원 정보 ──
    private Long empId;
    private String empName;
    private String deptName;
    private String gradeName;
    private String retirementType;      // "severance" / "DB" / "DC"

    // ── 근속 ──
    private LocalDate hireDate;
    private LocalDate baseDate;
    private Long serviceDays;
    private BigDecimal serviceYears;    // 소수점 2자리

    // ── 평균임금 구성 (계산 투명성) ──
    private Long last3MonthPay;
    private Long lastYearBonus;
    private Long annualLeaveAllowance;
    private Integer last3MonthDays;
    private BigDecimal avgDailyWage;

    // ── 추계 결과 ──
    private Long estimatedSeverance;    // 공식에 의한 예상 퇴직금 전체
    private Long dcDepositedTotal;      // DC형 전용, 그 외 null
    private Long dcDiffAmount;          // DC형 전용, 그 외 null
    private Long displayAmount;         // 화면에 크게 보여줄 값 (severance/DB=estimatedSeverance, DC=dcDiffAmount)


    private LocalDateTime calculatedAt;

}

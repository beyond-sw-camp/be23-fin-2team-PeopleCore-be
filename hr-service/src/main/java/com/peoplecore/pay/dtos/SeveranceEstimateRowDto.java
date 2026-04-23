package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeveranceEstimateRowDto {
//    사원별 행
    private Long empId;
    private String empName;

    private String deptName;
    private String gradeName;

    private LocalDate hireDate;
    private BigDecimal serviceYears;

    private String retirementType;        // "severance" / "DB" / "DC"

    private BigDecimal avgDailyWage;
    private Long estimatedSeverance;

    // DC 전용 (severance/DB는 null)
    private Long dcDepositedTotal;
    private Long dcDiffAmount;

    // 화면 표시용 실제 회사 부담 추정액
    private Long displayAmount;
}

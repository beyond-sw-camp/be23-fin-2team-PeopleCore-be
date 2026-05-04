package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExpectedDeductionResDto {
    //월급여 예상지급공제 - 테이블 행

    private Long empId;
    private String empStatus;
    private String empNum;
    private String empName;
    private String deptName;
    private String titleName;

    private BigDecimal annualSalary;
    private Long monthlySalary;
    private Long basePay;              //기본급

    private Long nationalPension;
    private Long healthInsurance;
    private Long longTermCare;
    private Long employmentInsurance;

    private Long incomeTax;
    private Long localIncomeTax;

    private Long totalDeduction;    //공제 합계
    private Long expectedNetPay;    //예상 세후 월급




}

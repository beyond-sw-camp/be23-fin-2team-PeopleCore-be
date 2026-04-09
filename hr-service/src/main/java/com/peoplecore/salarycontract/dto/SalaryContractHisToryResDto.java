package com.peoplecore.salarycontract.dto;


import com.peoplecore.pay.domain.PayrollDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class SalaryContractHisToryResDto {
    private Long id;
    private String empNum;
    private String empName;
    private String department;
    private String rank;
    private Integer year;
    private BigDecimal annualSalary; //계약 연봉
    private LocalDate contractStart;
    private LocalDate contractEnd; //계약 종료일 (정규직은 null)
    private BigDecimal salaryDiff; //전년 대비 연봉차이(원)
    private BigDecimal salaryDiffRate; //전년 대비 변동률 (%)

    }


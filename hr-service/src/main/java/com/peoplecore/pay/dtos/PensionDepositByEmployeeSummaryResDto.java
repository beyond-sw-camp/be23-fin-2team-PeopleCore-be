package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PensionDepositByEmployeeSummaryResDto {
//    사원 집계 요청
    private Integer totalEmployees;
    private Long totalDepositAmount;
    private Long monthlyAverage;
    private Long grandTotalDeposited;
    private List<PensionDepositByEmployeeResDto> employees;

}

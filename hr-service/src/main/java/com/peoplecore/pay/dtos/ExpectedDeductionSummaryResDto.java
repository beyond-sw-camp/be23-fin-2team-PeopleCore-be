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
public class ExpectedDeductionSummaryResDto {
//    월급여 예상지급공제 - 상단 요약

    private Integer totalEmployees;
    private Long totalExpectedNetPay;
    private List<ExpectedDeductionResDto> employees;
}

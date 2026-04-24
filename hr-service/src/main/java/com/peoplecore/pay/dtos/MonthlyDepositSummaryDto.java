package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MonthlyDepositSummaryDto {
//    월별 집계
    private String yearMonth;
    private Integer count;
    private Long totalAmount;

}

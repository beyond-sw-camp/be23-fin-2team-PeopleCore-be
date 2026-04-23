package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PensionDepositSummaryResDto {
    private Integer totalEmployees;
    private Long totalDepositAmount;
    private Long monthlyAverage;
    private Long grandTotalDeposited;
    private Page<PensionDepositResDto> deposits;
}

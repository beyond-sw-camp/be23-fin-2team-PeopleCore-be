package com.peoplecore.pay.dtos;

import com.peoplecore.pay.domain.TaxWithholdingTable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaxWithholdingResDto {
//급여계산용 (단건, 특정사원의 세액)

    private Integer taxYear;
    private Integer salaryMin;       // 천원
    private Integer salaryMax;
    private Integer dependents;
    private Long incomeTax;          // 원
    private Long localIncomeTax;     // 원 (소득세 × 10%)

}

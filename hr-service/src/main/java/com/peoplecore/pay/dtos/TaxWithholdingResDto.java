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

    private Long taxId;
    private Integer taxYear;
    private Long salaryFrom;
    private Long salaryTo;
    private Integer dependents;
    private Long incomeTax;
    private Long localIncomeTax;

    public static TaxWithholdingResDto fromEntity(TaxWithholdingTable t){
        Long incomeTax = t.getIncomeTax();
        return TaxWithholdingResDto.builder()
                .taxId(t.getTaxId())
                .taxYear(t.getTaxYear())
                .salaryFrom(t.getSalaryFrom())
                .salaryTo(t.getSalaryTo())
                .dependents(t.getDependents())
                .incomeTax(incomeTax)
                .localIncomeTax(incomeTax != null ? Math.round(incomeTax * 0.1) : 0L)
                .build();
    }
}

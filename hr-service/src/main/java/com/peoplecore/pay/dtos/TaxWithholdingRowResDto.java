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
public class TaxWithholdingRowResDto {
// 간이세액표 조회용

    private Long taxId;
    private Integer taxYear;
    private Integer salaryMin;     // 천원 단위
    private Integer salaryMax;
    private Integer taxDep01;
    private Integer taxDep02;
    private Integer taxDep03;
    private Integer taxDep04;
    private Integer taxDep05;
    private Integer taxDep06;
    private Integer taxDep07;
    private Integer taxDep08;
    private Integer taxDep09;
    private Integer taxDep10;
    private Integer taxDep11;

    public static TaxWithholdingRowResDto fromEntity(TaxWithholdingTable t) {
        return TaxWithholdingRowResDto.builder()
                .taxId(t.getTaxId())
                .taxYear(t.getTaxYear())
                .salaryMin(t.getSalaryMin())
                .salaryMax(t.getSalaryMax())
                .taxDep01(t.getTaxDep01())
                .taxDep02(t.getTaxDep02())
                .taxDep03(t.getTaxDep03())
                .taxDep04(t.getTaxDep04())
                .taxDep05(t.getTaxDep05())
                .taxDep06(t.getTaxDep06())
                .taxDep07(t.getTaxDep07())
                .taxDep08(t.getTaxDep08())
                .taxDep09(t.getTaxDep09())
                .taxDep10(t.getTaxDep10())
                .taxDep11(t.getTaxDep11())
                .build();
    }
}

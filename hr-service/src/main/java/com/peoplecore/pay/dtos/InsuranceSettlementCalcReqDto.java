package com.peoplecore.pay.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceSettlementCalcReqDto {

//    정산시작월
    @NotBlank(message = "정산 시작월을 선택해주세요")
    private String fromYearMonth;
    @NotBlank(message = "정산 종료월을 선택해주세요")
    private String toYearMonth;
}

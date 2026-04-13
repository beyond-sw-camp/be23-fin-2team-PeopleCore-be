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
public class InsuranceSettlementApplyReqDto {
//    정산보험료를 급여대장에 일괄반영할때 요청dto

    /* 반영대상 급여대장 월 */
    @NotBlank(message = "반영대상 급여대장 월 선택은 필수입니다")
    private String targetPayYearMonth;

    /* 정산 시작월 */
    @NotBlank(message = "정산 시작월은 필수입니다")
    private String fromYearMonth;

    /* 정산 종료월 */
    @NotBlank(message = "정산 종료월은 필수입니다")
    private String toYearMonth;

}

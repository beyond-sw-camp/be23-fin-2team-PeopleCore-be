package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalcDeductionReqDto {

    private Long totalPay;  //지급합계(지급항목 전체 합)
    private Long empId;     //사원ID (부양가족수, 세율옵션 조회용)

}

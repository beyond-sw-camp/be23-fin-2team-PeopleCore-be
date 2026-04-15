package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CalcDeductionResDto {

//    사대보험(근로자 부담분)
    private Long nationalPension;
    private Long healthInsurance;
    private Long longTermCare;
    private Long employmentInsurance;

//    세금
    private Long incomeTax;
    private Long localIncomeTax;

//    합계
    private Long totalDeduction;    //공제합계
    private Long netPay;            //공제 후 지급액

}

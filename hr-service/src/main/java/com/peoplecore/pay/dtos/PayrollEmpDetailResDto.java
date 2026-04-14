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
public class PayrollEmpDetailResDto {

    private Long empID;
    private String empName;
    private String deptName;
    private String gradeName;
    private String empType;

    private List<PayrollItemDto> paymentItems;  //지급 항목 목록
    private List<PayrollItemDto> deductionItems;  //공제 항목 목록

    private Long totalPay;
    private Long totalDeduction;
    private Long netPay;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayrollItemDto{
        private Long payItemId;
        private String payItemName; //스냅샷 항목명
        private Long amount;
    }


}

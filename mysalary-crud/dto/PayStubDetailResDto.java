package com.peoplecore.pay.dto;

import com.peoplecore.pay.domain.PayStubs;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 급여명세서 상세 응답 DTO
 * 지급항목 / 공제항목 분리 + 총액
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayStubDetailResDto {

    private Long payStubsId;
    private String payYearMonth;
    private Long empId;
    private String empName;
    private String deptName;

    // 지급 항목
    private List<PayStubItemResDto> paymentItems;
    private Long totalPay;

    // 공제 항목
    private List<PayStubItemResDto> deductionItems;
    private Long totalDeduction;

    // 실수령액
    private Long netPay;

    private String pdfUrl;

    public static PayStubDetailResDto fromEntity(
            PayStubs stub,
            String empName,
            String deptName,
            List<PayStubItemResDto> paymentItems,
            List<PayStubItemResDto> deductionItems) {

        return PayStubDetailResDto.builder()
                .payStubsId(stub.getPayStubsId())
                .payYearMonth(stub.getPayYearMonth())
                .empId(stub.getEmpId())
                .empName(empName)
                .deptName(deptName)
                .paymentItems(paymentItems)
                .totalPay(stub.getTotalPay())
                .deductionItems(deductionItems)
                .totalDeduction(stub.getTotalDeduction())
                .netPay(stub.getNetPay())
                .pdfUrl(stub.getPdfUrl())
                .build();
    }
}

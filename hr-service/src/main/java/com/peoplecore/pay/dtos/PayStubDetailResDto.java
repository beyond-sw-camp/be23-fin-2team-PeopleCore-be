package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PayStubDetailResDto {
//    급여명세서 상세
    private Long stubId;
    private String payYearMonth;
    private LocalDateTime issuedAt;

    // 사원·부서 스냅샷
    private String empName;
    private String deptName;

    // 합계
    private Long totalPay;
    private Long totalDeduction;
    private Long netPay;

    // 항목 분류 (지급/공제)
    private List<PayStubItemResDto> paymentItems;
    private List<PayStubItemResDto> deductionItems;

    // 파일
    private String pdfUrl;

}

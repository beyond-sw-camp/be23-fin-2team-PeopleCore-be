package com.peoplecore.pay.dto;

import com.peoplecore.pay.domain.PayStubs;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 급(상)여명세서 목록 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayStubListResDto {

    private Long payStubsId;
    private String payYearMonth;     // "2026-03" 형식
    private Long totalPay;           // 총 지급액
    private Long totalDeduction;     // 총 공제액
    private Long netPay;             // 실수령액
    private String sendStatus;       // 발송 상태

    public static PayStubListResDto fromEntity(PayStubs stub) {
        return PayStubListResDto.builder()
                .payStubsId(stub.getPayStubsId())
                .payYearMonth(stub.getPayYearMonth())
                .totalPay(stub.getTotalPay())
                .totalDeduction(stub.getTotalDeduction())
                .netPay(stub.getNetPay())
                .sendStatus(stub.getSendStatus() != null ? stub.getSendStatus().name() : null)
                .build();
    }
}

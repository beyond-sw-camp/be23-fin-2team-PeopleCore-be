package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PayStubListResDto {
//    연도별 명세서 목록

    private Long stubId;                // entity: payStubsId
    private String payYearMonth;        // "YYYY-MM"
    private LocalDateTime issuedAt;     // entity: issuedAt
    private Long totalPay;
    private Long totalDeduction;
    private Long netPay;
    private String sendStatus;          // entity: sendStatus.name()
}
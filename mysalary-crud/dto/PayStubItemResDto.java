package com.peoplecore.pay.dto;

import com.peoplecore.pay.enums.PayItemType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 급여명세서 상세 - 개별 항목 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayStubItemResDto {

    private Long payItemId;
    private String payItemName;    // 항목명 (기본급, 직책수당, 국민연금 등)
    private PayItemType payItemType;  // PAYMENT / DEDUCTION
    private Long amount;           // 금액
}

package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PayStubItemResDto {
//    명세서 개별 항목

    private Long payItemId;
    private String payItemName;
    private String payItemType;   // "PAYMENT" / "DEDUCTION"
    private String payItemCategory;
    private Long amount;
    private Boolean isTaxable;
}
package com.peoplecore.pay.dtos;

import com.peoplecore.pay.domain.PayItems;
import com.peoplecore.pay.enums.LegalCalcType;
import com.peoplecore.pay.enums.PayItemCategory;
import com.peoplecore.pay.enums.PayItemType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PayItemResDto {

    private Long payItemId;
    private String payItemName;
    private PayItemType payItemType;
    private Boolean isFixed;
    private Boolean isTaxable;
    private Integer taxExemptLimit;
    private PayItemCategory payItemCategory;
    private Integer sortOrder;
    private Boolean isActive;
    private Boolean isLegal;
    private LegalCalcType legalCalcType;
    private Boolean isProtect;

    public static PayItemResDto fromEntity(PayItems p){
        return PayItemResDto.builder()
                .payItemId(p.getPayItemId())
                .payItemName(p.getPayItemName())
                .payItemType(p.getPayItemType())
                .isFixed(p.getIsFixed())
                .isTaxable(p.getIsTaxable())
                .taxExemptLimit(p.getTaxExemptLimit())
                .payItemCategory(p.getPayItemCategory())
                .sortOrder(p.getSortOrder())
                .isActive(p.getIsActive())
                .isLegal(p.getIsLegal())
                .legalCalcType(p.getLegalCalcType())
                .isProtect(p.getIsProtected())
                .build();
    }

}

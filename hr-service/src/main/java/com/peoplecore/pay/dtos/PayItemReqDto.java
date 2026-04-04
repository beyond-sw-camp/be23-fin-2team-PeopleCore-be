package com.peoplecore.pay.dtos;

import com.peoplecore.pay.enums.PayItemCategory;
import com.peoplecore.pay.enums.PayItemType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PayItemReqDto {

    @NotBlank(message = "항목명은 필수입니다")
    private String payItemName;
    @NotNull(message = "지급/공제 구분은 필수입니다")
    private PayItemType payItemType;

    @Builder.Default
    private Boolean isFixed = false;    //고정수당여부 (지급항목만)
    @Builder.Default
    private Boolean isTaxable = true;   //과세여부 (지급항목만)

    @Builder.Default
    @Min(value = 0, message = "비과세한도는 0이상이어야 합니다")
    private Integer taxExemptLimit = 0; //비과세한도 (지급항목만)

    private PayItemCategory payItemCategory;    //항목 분류
    private Integer sortOrder;

}

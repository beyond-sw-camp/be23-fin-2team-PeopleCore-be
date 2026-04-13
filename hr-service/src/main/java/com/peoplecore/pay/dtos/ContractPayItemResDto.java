package com.peoplecore.pay.dtos;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ContractPayItemResDto {

//    고정수당 항목
    private Long payItemId;
    private String payItemName;
    private Integer amount;

}

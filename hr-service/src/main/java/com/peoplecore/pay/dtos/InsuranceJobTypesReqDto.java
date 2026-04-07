package com.peoplecore.pay.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsuranceJobTypesReqDto {

    @NotBlank(message = "업종명은 필수입니다")
    private String name;

    private String desciption;

    @NotNull(message = "산재보험요율은 필수입니다")
    @DecimalMin( value = "0.0001",message = "요율은 0보다 커야합니다")
    private BigDecimal industrialAccidentRate;

}

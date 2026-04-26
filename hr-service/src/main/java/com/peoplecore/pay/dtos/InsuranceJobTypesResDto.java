package com.peoplecore.pay.dtos;

import com.peoplecore.pay.domain.InsuranceJobTypes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InsuranceJobTypesResDto {

    private Long jobTypesId;
    private String name;
    private String description;
    private BigDecimal industrialAccidentRate;
    private Boolean isActive;

    public static InsuranceJobTypesResDto fromEntity(InsuranceJobTypes j){
        return InsuranceJobTypesResDto.builder()
                .jobTypesId(j.getJobTypesId())
                .name(j.getJobTypeName())
                .description(j.getDescription())
                .industrialAccidentRate(j.getIndustrialAccidentRate())
                .isActive(j.getIsActive())
                .build();
    }

}

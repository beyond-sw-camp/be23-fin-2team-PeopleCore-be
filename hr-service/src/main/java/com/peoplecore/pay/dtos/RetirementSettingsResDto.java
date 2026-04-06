package com.peoplecore.pay.dtos;

import com.peoplecore.pay.domain.RetirementSettings;
import com.peoplecore.pay.enums.PensionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RetirementSettingsResDto {

    private Long retirementSettingsId;
    private PensionType pensionType;
    private String pensionProvider;
    private String pensionAccount;

    public static RetirementSettingsResDto fromEntity(RetirementSettings s){
        return RetirementSettingsResDto.builder()
                .retirementSettingsId(s.getRetirementSettingsId())
                .pensionType(s.getPensionType())
                .pensionProvider(s.getPensionProvider())
                .pensionAccount(s.getPensionAccount())
                .build();
    }


}

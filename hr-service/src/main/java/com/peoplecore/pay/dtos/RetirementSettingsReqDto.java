package com.peoplecore.pay.dtos;

import com.peoplecore.pay.enums.PensionType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetirementSettingsReqDto {

    @NotNull(message = "퇴직연금 제도를 선택해주세요")
    private PensionType pensionType;

//    DB, DB_DC형일때만 입력
    private String pensionProvider;
    private String pensionAccount;
}

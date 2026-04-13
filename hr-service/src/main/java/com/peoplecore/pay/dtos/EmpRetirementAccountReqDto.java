package com.peoplecore.pay.dtos;

import com.peoplecore.pay.enums.RetirementType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmpRetirementAccountReqDto {

    @NotBlank(message = "퇴직연금 운용사는 필수입니다")
    private RetirementType retirementType;
    @NotBlank(message = "퇴직연금 계좌번호는 필수입니다")
    private String pensionProvider;

    private String accountNumber;


}

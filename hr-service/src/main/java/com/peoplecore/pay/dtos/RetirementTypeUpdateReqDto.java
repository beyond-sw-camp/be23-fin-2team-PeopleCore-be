package com.peoplecore.pay.dtos;

import com.peoplecore.pay.enums.RetirementType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetirementTypeUpdateReqDto {

    @NotBlank(message = "퇴직연금 유형은 필수입니다")
    private RetirementType retirementType; //DB, DC
}

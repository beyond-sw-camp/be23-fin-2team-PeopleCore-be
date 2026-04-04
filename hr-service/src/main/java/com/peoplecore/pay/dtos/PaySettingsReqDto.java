package com.peoplecore.pay.dtos;

import com.peoplecore.pay.domain.CompanyPaySettings;
import com.peoplecore.pay.enums.PayMonth;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaySettingsReqDto {

    @NotNull(message = "지급 기준을 선택해주세요.")
    private PayMonth salaryPayMonth;    //NEXT, CURRENT

    @Min(value = 1, message = "지급일은 최소 1일입니다.")
    @Max(value = 31, message = "지급일은 최대 31일입니다.")
    private Integer salaryPayDay;

    @NotNull(message = "말일 여부를 선택해주세요.")
    private Boolean salaryPayLastDay;

    @NotBlank(message = "주거래은행을 선택해주세요.")
    private String mainBankCode;

}

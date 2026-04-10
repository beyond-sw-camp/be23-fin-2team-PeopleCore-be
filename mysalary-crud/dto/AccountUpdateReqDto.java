package com.peoplecore.pay.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 급여 계좌 변경 요청 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AccountUpdateReqDto {

    @NotBlank(message = "은행명은 필수입니다.")
    private String bankName;

    @NotBlank(message = "계좌번호는 필수입니다.")
    private String accountNumber;

    @NotBlank(message = "예금주는 필수입니다.")
    private String accountHolder;
}

package com.peoplecore.pay.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountVerifyReqDto {

    @NotBlank(message = "은행코드는 필수입니다")
    private String bankCode;        // 표준은행코드 3자리 (예: "088")

    @NotBlank(message = "계좌번호는 필수입니다")
    private String accountNumber;

    @NotBlank(message = "예금주는 필수입니다")
    private String accountHolder;
}

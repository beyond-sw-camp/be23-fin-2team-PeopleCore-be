package com.peoplecore.pay.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmpAccountReqDto {

    @NotBlank(message = "은행명은 필수입니다")
    private String bankName;
    @NotBlank(message = "계좌번호는 필수입니다")
    private String accountNumber;
    @NotBlank(message = "예금주는 필수입니다")
    private String accountHolder;
    @NotBlank(message = "계좌검증이 필요합니다")
    private String verificationToken;
}

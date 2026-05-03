package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountVerifyResDto {

    private boolean verified;
    private String holder;                  // 오픈뱅킹이 응답한 실명 (참고용)
    private String verificationToken;       // 저장 단계로 들고 가야 하는 토큰
    private long expiresIn;                 // 토큰 유효시간(초)
}

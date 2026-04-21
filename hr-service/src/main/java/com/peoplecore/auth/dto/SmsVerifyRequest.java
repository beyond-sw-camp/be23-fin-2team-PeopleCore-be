package com.peoplecore.auth.dto;

import lombok.*;

import java.util.UUID;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class SmsVerifyRequest {
    private UUID companyId;
    private String empName;
    private String empPhone;
    private String code;
    private String empBirthDate; // yyyyMMdd (이메일 찾기 시에만 사용)
}
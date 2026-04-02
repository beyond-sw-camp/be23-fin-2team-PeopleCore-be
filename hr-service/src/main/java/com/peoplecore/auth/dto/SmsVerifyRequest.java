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
}
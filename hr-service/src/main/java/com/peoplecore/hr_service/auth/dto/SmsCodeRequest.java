package com.peoplecore.hr_service.auth.dto;

import lombok.*;

import java.util.UUID;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class SmsCodeRequest {
    private UUID companyId;
    private String empName;
    private String empPhone;
}
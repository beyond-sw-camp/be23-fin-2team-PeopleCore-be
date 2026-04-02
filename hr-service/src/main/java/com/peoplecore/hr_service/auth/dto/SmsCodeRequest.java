package com.peoplecore.hr_service.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SmsCodeRequest {
    private UUID companyId;
    private String empName;
    private String empPhone;
}
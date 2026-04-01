package com.peoplecore.hr_service.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    private UUID companyId;
    private String email;
    private String password;
}
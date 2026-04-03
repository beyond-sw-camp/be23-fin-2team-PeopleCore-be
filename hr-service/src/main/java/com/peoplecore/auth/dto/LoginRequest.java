package com.peoplecore.auth.dto;

import lombok.*;

import java.util.UUID;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class LoginRequest {
    private UUID companyId;
    private String email;
    private String password;
}
package com.peoplecore.auth.dto;

import lombok.*;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private String empName;
    private String empRole;
}
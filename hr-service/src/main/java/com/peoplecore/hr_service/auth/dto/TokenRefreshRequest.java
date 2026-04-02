package com.peoplecore.hr_service.auth.dto;

import lombok.*;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class TokenRefreshRequest {
    private String refreshToken;
}
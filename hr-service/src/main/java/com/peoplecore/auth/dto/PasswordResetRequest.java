package com.peoplecore.auth.dto;

import lombok.*;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class PasswordResetRequest {
    private String empPhone;
    private String newPassword;
}
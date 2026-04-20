package com.peoplecore.auth.dto;

import lombok.*;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class PasswordResetByEmailRequest {
    private String empEmail;
    private String newPassword;
}

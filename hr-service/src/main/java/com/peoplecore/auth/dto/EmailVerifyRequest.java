package com.peoplecore.auth.dto;

import lombok.*;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class EmailVerifyRequest {
    private String empEmail;
    private String code;
}

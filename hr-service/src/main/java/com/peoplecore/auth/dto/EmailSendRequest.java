package com.peoplecore.auth.dto;

import lombok.*;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class EmailSendRequest {
    private String empEmail;
}

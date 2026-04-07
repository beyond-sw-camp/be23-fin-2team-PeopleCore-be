package com.peoplecore.auth.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaceHealthResponse {
    private String status;
    private String message;
}
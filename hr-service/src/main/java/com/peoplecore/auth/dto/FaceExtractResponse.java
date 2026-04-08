package com.peoplecore.auth.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaceExtractResponse {
    private String status;
    private List<Double> embedding;
    private String message;
}
package com.peoplecore.evaluation.dto;

import lombok.*;

// GET /evaluator-role/me 응답. JSON 키 evaluator
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyEvaluatorRoleResponse {
    private boolean evaluator;
}

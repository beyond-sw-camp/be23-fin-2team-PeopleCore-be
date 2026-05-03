package com.peoplecore.evaluation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// PATCH /emp-evaluator/global/{empId} — 평가자 1명 즉시 변경 요청
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmpEvaluatorChangeRequest {

    @NotNull
    private Long newEvaluatorId;
}

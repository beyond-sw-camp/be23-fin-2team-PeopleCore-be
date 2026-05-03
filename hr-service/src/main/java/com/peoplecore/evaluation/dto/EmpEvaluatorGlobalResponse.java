package com.peoplecore.evaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

// GET /emp-evaluator/global 응답
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmpEvaluatorGlobalResponse {

    private List<EmpEvaluatorMappingDto> mappings;
}

package com.peoplecore.evaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 매핑 1건 응답. excluded=true 면 evaluator* 필드는 null.
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmpEvaluatorMappingDto {

    private Long evaluateeEmpId;

    private String evaluateeName;

    private String evaluateeDeptName;

    private Long evaluatorEmpId;

    private String evaluatorName;

    private String evaluatorDeptName;

    private boolean excluded;  // 평가 제외 여부
}

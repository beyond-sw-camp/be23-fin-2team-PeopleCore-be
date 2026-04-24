package com.peoplecore.evaluation.dto;

import com.peoplecore.evaluation.domain.EvaluatorRoleMode;
import lombok.*;

import java.util.List;

// GET /evaluator-role/preview 응답
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluatorRolePreviewResponse {
    private EvaluatorRoleMode mode;
    private Long grantedTargetId;
    private List<DeptResolutionDto> depts;
}

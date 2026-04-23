package com.peoplecore.evaluation.dto;

import com.peoplecore.evaluation.domain.EvaluatorRoleMode;
import lombok.*;

import java.util.List;

// GET /evaluator-role/config 응답. grantedTargetId=null 이면 미설정.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluatorRoleConfigResponse {
    private EvaluatorRoleMode mode; //직급or직책
    private Long grantedTargetId;
    // 저장된 부서별 배정 (자동배정 + HR 지정 모두 포함)
    private List<DeptOverrideDto> overrides;
}

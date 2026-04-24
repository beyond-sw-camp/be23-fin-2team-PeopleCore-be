package com.peoplecore.evaluation.dto;

import com.peoplecore.evaluation.domain.EvaluatorRoleMode;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

// PUT /evaluator-role/config 요청. mode 와 grantedTargetId 필수. overrides 는 conflict 부서만.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluatorRoleUpdateRequest {

    @NotNull
    private EvaluatorRoleMode mode; // 직급of직책

    @NotNull
    private Long grantedTargetId;

    // 같은 부서에 후보가 여러 명인 경우 HR 이 지정한 1명. 1명 부서는 서버가 자동 배정.
    private List<DeptOverrideDto> overrides;
}

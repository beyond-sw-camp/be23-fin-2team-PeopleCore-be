package com.peoplecore.evaluation.dto;

import com.peoplecore.evaluation.domain.KpiDirection;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class KpiTemplateRequest {

    @NotNull(message = "부서는 필수입니다")
    private Long deptId; //Department.deptId

    // 직급 — null 이면 해당 부서 전 직급 공통 KPI
    private Long gradeId;

    @NotNull(message = "카테고리는 필수입니다")
    private Long categoryOptionId; // KpiOption(CATEGORY).optionId

    @NotNull(message = "단위는 필수입니다")
    private Long unitOptionId; // KpiOption(UNIT).optionId

    @NotNull(message = "지표명은 필수입니다")
    private String name;

    @NotNull(message = "설명은 필수입니다")
    private String description;

    @NotNull(message = "지표 방향성은 필수입니다")
    private KpiDirection direction;   // UP / DOWN / MAINTAIN
}

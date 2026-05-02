package com.peoplecore.evaluation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

// PUT /emp-evaluator/global — 글로벌 매핑 일괄 교체 요청
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmpEvaluatorUpdateRequest {

    @NotNull
    @Valid
    private List<MappingItem> mappings;

    // 매핑 1건 — 피평가자/평가자 empId 페어. excluded=true 면 evaluatorEmpId 는 null.
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MappingItem {

        @NotNull
        private Long evaluateeEmpId;

        // excluded=true 면 null 허용
        private Long evaluatorEmpId;

        private boolean excluded;
    }
}

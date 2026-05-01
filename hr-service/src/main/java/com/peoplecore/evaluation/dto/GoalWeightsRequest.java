package com.peoplecore.evaluation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 가중치 일괄 저장 요청 — PUT /eval/goals/weights
//   본인의 KPI 목표 weight 를 한 번에 저장 (임시저장 — 합계 100 검증은 제출 시점에만)
//   요청 항목 중 OKR 또는 본인 소유 아닌 목표는 서비스에서 거절
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GoalWeightsRequest {

    @NotEmpty
    @Valid
    private List<Item> items;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Item {
        @NotNull
        private Long goalId;

        @NotNull
        @Min(10)
        @Max(100)
        private Integer weight;
    }
}

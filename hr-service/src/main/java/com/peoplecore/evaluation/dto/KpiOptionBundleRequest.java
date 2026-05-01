package com.peoplecore.evaluation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// KPI 옵션 번들 저장 요청 — 각 항목에 id 포함으로 rename/재정렬 감지 가능
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KpiOptionBundleRequest {

    @NotNull
    private List<ItemRequest> categories;

    @NotNull
    private List<ItemRequest> units;

    // 옵션 1건 요청 단위
    //   - id null -> 신규 추가 (INSERT)
    //   - id 있음 -> 기존 row. label 바뀌었으면 rename, 아니면 순서만 체크
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ItemRequest {
        private Long id;
        @NotNull
        private String label;
    }
}

package com.peoplecore.evaluation.dto;

import com.peoplecore.evaluation.domain.KpiOption;
import com.peoplecore.evaluation.domain.KpiOptionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// KPI 옵션 번들 응답 — 카테고리/단위 한 번에 내려줌
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class KpiOptionBundleResponse {

    // 각 항목에 DB id 포함 — 프론트가 저장 요청 시 이 id 로 매칭되어 rename/재정렬 감지
    private List<OptionItem> categories;
    private List<OptionItem> units;

    // 옵션 1건 — id + label (label 은 option_value 를 그대로 노출)
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OptionItem {
        private Long id;
        private String label;
    }

    // KpiOption row 들 → 번들 DTO 조립
    public static KpiOptionBundleResponse from(Collection<KpiOption> rows) {
        List<OptionItem> categories = new ArrayList<>();
        List<OptionItem> units = new ArrayList<>();

        for (KpiOption o : rows) {
            if (o.getType() == KpiOptionType.CATEGORY) {
                categories.add(OptionItem.builder()
                        .id(o.getOptionId()).label(o.getOptionValue()).build());
            } else if (o.getType() == KpiOptionType.UNIT) {
                units.add(OptionItem.builder()
                        .id(o.getOptionId()).label(o.getOptionValue()).build());
            }
        }

        return KpiOptionBundleResponse.builder()
                .categories(categories)
                .units(units)
                .build();
    }
}

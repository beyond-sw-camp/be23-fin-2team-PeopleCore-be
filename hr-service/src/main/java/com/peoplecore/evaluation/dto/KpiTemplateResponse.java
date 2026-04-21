package com.peoplecore.evaluation.dto;

import com.peoplecore.evaluation.domain.KpiDirection;
import com.peoplecore.evaluation.domain.KpiTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// KPI 지표 1건 응답
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KpiTemplateResponse {
    private Long kpiId;
    private Long deptId;            // Department.deptId - 실제 조직도 부서 PK
    private String deptName;
    private Long categoryOptionId;  // KpiOption(CATEGORY).optionId
    private String categoryLabel;   // KpiOption(CATEGORY).optionValue
    private Long unitOptionId;      // KpiOption(UNIT).optionId
    private String unitLabel;       // KpiOption(UNIT).optionValue
    private String name;            // 지표명
    private String description;
    private BigDecimal baseline;    // 사내평균 (없으면 null - 집계 전 또는 미사용)
    private KpiDirection direction; // 지표 방향성 (UP/DOWN/MAINTAIN)

    // Entity -> DTO 변환
    // baseline 만 nullable (집계 전에 null)
    public static KpiTemplateResponse from(KpiTemplate t) {
        return KpiTemplateResponse.builder()
                .kpiId(t.getKpiId())
                .deptId(t.getDepartment().getDeptId())
                .deptName(t.getDepartment().getDeptName())
                .categoryOptionId(t.getCategory().getOptionId())
                .categoryLabel(t.getCategory().getOptionValue())
                .unitOptionId(t.getUnit().getOptionId())
                .unitLabel(t.getUnit().getOptionValue())
                .name(t.getName())
                .description(t.getDescription())
                .baseline(t.getBaseline())
                .direction(t.getDirection())
                .build();
    }
}

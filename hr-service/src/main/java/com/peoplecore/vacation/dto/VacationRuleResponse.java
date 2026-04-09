package com.peoplecore.vacation.dto;

import com.peoplecore.vacation.entity.VacationCreateRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationRuleResponse {
    private Long id;
    private Integer minYears;
    private Integer maxYears;
    private Integer days;
    private String desc;

    public static VacationRuleResponse from(VacationCreateRule rule) {
        return VacationRuleResponse.builder()
                .id(rule.getCreateRuleId())
                .minYears(rule.getCreateRuleMinYear())
                .maxYears(rule.getCreateRuleMaxYear())
                .days(rule.getCreateRuleDay())
                .desc(rule.getCreateRuleDesc())
                .build();
    }
}

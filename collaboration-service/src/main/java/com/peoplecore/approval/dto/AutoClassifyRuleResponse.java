package com.peoplecore.approval.dto;

import com.peoplecore.approval.entity.AutoClassifyRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoClassifyRuleResponse {

    private Long id;
    private String ruleName;
    private Conditions conditions;
    private Long targetFolderId;
    private String targetFolderName;
    private Boolean isActive;
    private Integer sortOrder;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Conditions {
        private String titleContains;
        private String formName;
        private String drafterDept;
        private String drafterName;
    }

    public static AutoClassifyRuleResponse from(AutoClassifyRule rule, String targetFolderName) {
        return AutoClassifyRuleResponse.builder()
                .id(rule.getRuleId())
                .ruleName(rule.getRuleName())
                .conditions(Conditions.builder()
                        .titleContains(rule.getTitleContains())
                        .formName(rule.getFormName())
                        .drafterDept(rule.getDrafterDept())
                        .drafterName(rule.getDrafterName())
                        .build())
                .targetFolderId(rule.getTargetFolderId())
                .targetFolderName(targetFolderName)
                .isActive(rule.getIsActive())
                .sortOrder(rule.getSortOrder())
                .build();
    }
}

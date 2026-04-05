package com.peoplecore.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoClassifyRuleCreateRequest {

    private String ruleName;
    private Conditions conditions;
    private Long targetFolderId;
    private Boolean isActive;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Conditions {
        private String titleContains;
        private String formName;
        private String drafterDept;
        private String drafterName;
    }
}

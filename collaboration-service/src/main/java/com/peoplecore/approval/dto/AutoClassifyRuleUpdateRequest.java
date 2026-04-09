package com.peoplecore.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoClassifyRuleUpdateRequest {

    private String ruleName;
    private AutoClassifyRuleCreateRequest.Conditions conditions;
    private Long targetFolderId;
    private Boolean isActive;
    private String sourceBox;       // "SENT" 또는 "INBOX"
}

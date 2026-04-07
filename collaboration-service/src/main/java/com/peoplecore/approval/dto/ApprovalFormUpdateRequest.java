package com.peoplecore.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalFormUpdateRequest {
    private String formName;
    private String formHtml;
    private String formWritePermission;
    private Boolean formIsPublic;
    private Integer formRetentionYear;
    private Boolean formMobileYn;
    private Boolean formPreApprovalYn;
}

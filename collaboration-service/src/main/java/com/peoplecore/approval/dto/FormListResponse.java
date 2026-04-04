package com.peoplecore.approval.dto;

import com.peoplecore.approval.entity.ApprovalForm;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormListResponse {

    private Long formId;
    private String formName;
    private String formCode;
    private Long folderId;
    private String folderName;
    private Boolean isSystem;
    private Integer formVersion;
    private Boolean isActive;
    private String formWritePermission;
    private Boolean formIsPublic;
    private Integer formRetentionYear;
    private Boolean formMobileYn;
    private Boolean formPreApprovalYn;
    private Integer formSortOrder;

    public static FormListResponse from(ApprovalForm form) {
        return FormListResponse.builder()
                .formId(form.getFormId())
                .formName(form.getFormName())
                .formCode(form.getFormCode())
                .folderId(form.getFolderId().getFolderId())
                .folderName(form.getFolderId().getFolderName())
                .isSystem(form.getIsSystem())
                .formVersion(form.getFormVersion())
                .isActive(form.getIsActive())
                .formWritePermission(form.getFormWritePermission())
                .formIsPublic(form.getFormIsPublic())
                .formRetentionYear(form.getFormRetentionYear())
                .formMobileYn(form.getFormMobileYn())
                .formPreApprovalYn(form.getFormPreApprovalYn())
                .formSortOrder(form.getFormSortOrder())
                .build();
    }
}
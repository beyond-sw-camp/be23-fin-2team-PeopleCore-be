package com.peoplecore.approval.dto;

import com.peoplecore.approval.entity.ApprovalForm;
import com.peoplecore.approval.entity.FormWritePermission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormDetailResponse {
    private Long formId;
    private String formName;
    private String formCode;
    private String formHtml;
    private Long folderId;
    private String folderName;
    private Boolean isSystem;
    private Integer formVersion;
    private Boolean isCurrent;
    private Boolean isActive;
    private Long empId;
    private FormWritePermission formWritePermission;
    private Boolean formIsPublic;
    private Integer formRetentionYear;
    private Boolean formMobileYn;
    private Boolean formPreApprovalYn;
    private Integer formSortOrder;


    public static FormDetailResponse from(ApprovalForm form) {
        return FormDetailResponse.builder()
                .formId(form.getFormId())
                .formName(form.getFormName())
                .formCode(form.getFormCode())
                .formHtml(form.getFormHtml())
                .folderId(form.getFolderId().getFolderId())
                .folderName(form.getFolderId().getFolderName())
                .isSystem(form.getIsSystem())
                .formVersion(form.getFormVersion())
                .isCurrent(form.getIsCurrent())
                .isActive(form.getIsActive())
                .empId(form.getEmpId())
                .formWritePermission(form.getFormWritePermission())
                .formIsPublic(form.getFormIsPublic())
                .formRetentionYear(form.getFormRetentionYear())
                .formMobileYn(form.getFormMobileYn())
                .formPreApprovalYn(form.getFormPreApprovalYn())
                .formSortOrder(form.getFormSortOrder())
                .build();
    }
}

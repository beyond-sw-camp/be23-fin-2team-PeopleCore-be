package com.peoplecore.approval.dto;

import com.peoplecore.approval.entity.ApprovalForm;
import com.peoplecore.approval.entity.FormWritePermission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 관리자용 양식 목록 응답.
 * - 사원용 FormListResponse 대비 isCurrent / isProtected 추가
 * - isProtected: 보호 시스템 양식(수정·삭제 불가) 식별 → 관리자 UI 에서 버튼 비활성화 용도
 * - isCurrent: 항상 true 가 내려가지만 향후 옛 버전 포함 응답으로 확장될 가능성 대비
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormAdminListResponse {

    private Long formId;
    private String formName;
    private String formCode;
    private Long folderId;
    private String folderName;
    private Boolean isSystem;
    private Integer formVersion;
    private Boolean isCurrent;
    private Boolean isActive;
    private Boolean isProtected;
    private FormWritePermission formWritePermission;
    private Boolean formIsPublic;
    private Integer formRetentionYear;
    private Boolean formPreApprovalYn;
    private Integer formSortOrder;

    public static FormAdminListResponse from(ApprovalForm form) {
        return FormAdminListResponse.builder()
                .formId(form.getFormId())
                .formName(form.getFormName())
                .formCode(form.getFormCode())
                .folderId(form.getFolderId().getFolderId())
                .folderName(form.getFolderId().getFolderName())
                .isSystem(form.getIsSystem())
                .formVersion(form.getFormVersion())
                .isCurrent(form.getIsCurrent())
                .isActive(form.getIsActive())
                .isProtected(form.getIsProtected())
                .formWritePermission(form.getFormWritePermission())
                .formIsPublic(form.getFormIsPublic())
                .formRetentionYear(form.getFormRetentionYear())
                .formPreApprovalYn(form.getFormPreApprovalYn())
                .formSortOrder(form.getFormSortOrder())
                .build();
    }
}

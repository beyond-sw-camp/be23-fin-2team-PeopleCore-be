package com.peoplecore.approval.dto;

import com.peoplecore.approval.entity.FormWritePermission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalFormCreateRequest {
    private String formName;
    private String formCode;
    private String formHtml; // 양식 html 템플릿
    private Long folderId;
    private FormWritePermission formWritePermission; // 작성 권한 범위
    private Boolean formIsPublic; // 양식 공개 여부
    private Integer formRetentionYear; // 문서 보존 연한 ;
    private Boolean formMobileYn;
    private Boolean formPreApprovalYn; //사전 결재 허용 여부
}

package com.peoplecore.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentCreateRequest {
    /*양식 id
     *제목
     * 양식 유형
     * 양식 입력값(json)
     * 긴급 여부
     * */
    private Long formId;
    private String docTitle;
    private String docType;
    private String docData;
    private String docOpinion;
    private Boolean isEmergency;
    private Long personalFolderId;
    private Long deptFolderId;

    //    결재선 목록
    private List<ApprovalLineRequest> approvalLines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApprovalLineRequest {
        private Long empId;
        private String empName;
        private Long empDeptId;
        private String empDeptName;
        private String empGrade;
        private String empTitle;
        private String approvalRole;
        private Integer lineStep;
    }
}

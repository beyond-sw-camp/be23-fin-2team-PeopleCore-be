package com.peoplecore.approval.dto;


import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.approval.entity.ApprovalLine;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentDetailResponse {

    private Long docId;
    private String docNum;
    private String docTitle;
    private String docType;
    private String docData;
    private String approvalStatus;
    private Boolean isEmergency;
    private LocalDateTime docSubmittedAt;
    private LocalDateTime docCompleteAt;

    //    기안자 정보
    private Long empId;
    private String empName;
    private String empDeptName;
    private String empGrade;
    private String empTitle;

    //    양식 html
    private String formHtml;
    private String formName;
    private Long formId;

    //    결재선
    private List<ApprovalLineResponse> approvalLines;

    //    첨부파일
    private List<AttachmentListResponse> attachments;


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApprovalLineResponse {
        private Long lineId;
        private Long empId;
        private String empName;
        private String empDeptName;
        private String empGrade;
        private String empTitle;
        private String approvalRole;
        private Integer lineStep;
        private String approvalLineStatus;
        private LocalDateTime lineProcessedAt;
        private String lineRejectReason;
        private Boolean isDelegated;
        private Boolean isRead;
    }

    public static DocumentDetailResponse from(ApprovalDocument doc, List<ApprovalLine> lines) {
        List<ApprovalLineResponse> lineResponses = lines.stream().map(line -> ApprovalLineResponse.builder()
                .lineId(line.getLineId())
                .empId(line.getEmpId())
                .empName(line.getEmpName())
                .empDeptName(line.getEmpDeptName())
                .empGrade(line.getEmpGrade())
                .empTitle(line.getEmpTitle())
                .approvalRole(line.getApprovalRole().name())
                .lineStep(line.getLineStep())
                .approvalLineStatus(line.getApprovalLineStatus().name())
                .lineProcessedAt(line.getLineProcessedAt())
                .lineRejectReason(line.getLineRejectReason())
                .isDelegated(line.getIsDelegated())
                .isRead(line.getIsRead())
                .build()).toList();

        return DocumentDetailResponse.builder()
                .docId(doc.getDocId())
                .docNum(doc.getDocNum())
                .docTitle(doc.getDocTitle())
                .docData(doc.getDocData())
                .docType(doc.getDocType())
                .approvalStatus(doc.getApprovalStatus().name())
                .isEmergency(doc.getIsEmergency())
                .docSubmittedAt(doc.getDocSubmittedAt())
                .docCompleteAt(doc.getDocCompleteAt())
                .empId(doc.getEmpId())
                .empName(doc.getEmpName())
                .empDeptName(doc.getEmpDeptName())
                .empGrade(doc.getEmpGrade())
                .empTitle(doc.getEmpTitle())
                .formHtml(doc.getFormId().getFormHtml())
                .formName(doc.getFormId().getFormName())
                .formId(doc.getFormId().getFormId())
                .approvalLines(lineResponses).build();
    }
}

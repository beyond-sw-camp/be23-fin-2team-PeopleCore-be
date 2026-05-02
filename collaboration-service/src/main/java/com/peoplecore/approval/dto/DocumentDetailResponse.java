package com.peoplecore.approval.dto;


import com.peoplecore.approval.entity.ApprovalDocument;
import com.peoplecore.approval.entity.ApprovalLine;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentDetailResponse {

    private Long docId;
    private Long previousDocId;   // 재기안으로 생성된 경우 이전 문서 docId, 아니면 null
    private String docNum;
    private String docTitle;
    private String docType;
    private String docData;
    private String approvalStatus;
    private Boolean isEmergency;
    private Boolean isPublic;
    private LocalDateTime docSubmittedAt;
    private LocalDateTime docCompleteAt;
    private String docUrl;

    //    기안자 정보
    private Long empId;
    private String empName;
    private String empDeptName;
    private String empGrade;
    private String empTitle;
    private String docOpinion;
    private String drafterSigUrl;

    //    양식 html
    private String formHtml;
    private String formName;
    private Long formId;
    private String formCode;

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
        private String lineComment;
        private Boolean isDelegated;
        private Boolean isRead;
        private String sigUrl;
    }

    public static DocumentDetailResponse from(ApprovalDocument doc, List<ApprovalLine> lines) {
        return from(doc, lines, Map.of());
    }

    public static DocumentDetailResponse from(ApprovalDocument doc, List<ApprovalLine> lines, Map<Long, String> signatureMap) {
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
                .lineComment(line.getLineComment())
                .isDelegated(line.getIsDelegated())
                .isRead(line.getIsRead())
                .sigUrl(signatureMap.get(line.getEmpId()))
                .build()).toList();

        return DocumentDetailResponse.builder()
                .docId(doc.getDocId())
                .previousDocId(doc.getPreviousDocId())
                .docNum(doc.getDocNum())
                .docTitle(doc.getDocTitle())
                .docData(doc.getDocData())
                .docType(doc.getDocType())
                .docOpinion(doc.getDocOpinion())
                .approvalStatus(doc.getApprovalStatus().name())
                .isEmergency(doc.getIsEmergency())
                .isPublic(doc.getIsPublic())
                .docSubmittedAt(doc.getDocSubmittedAt())
                .docCompleteAt(doc.getDocCompleteAt())
                .docUrl(doc.getDocUrl())
                .empId(doc.getEmpId())
                .empName(doc.getEmpName())
                .empDeptName(doc.getEmpDeptName())
                .empGrade(doc.getEmpGrade())
                .empTitle(doc.getEmpTitle())
                .drafterSigUrl(signatureMap.get(doc.getEmpId()))
                .formHtml(doc.getFormId().getFormHtml())
                .formName(doc.getFormId().getFormName())
                .formId(doc.getFormId().getFormId())
                .formCode(doc.getFormId().getFormCode())
                .approvalLines(lineResponses).build();
    }
}

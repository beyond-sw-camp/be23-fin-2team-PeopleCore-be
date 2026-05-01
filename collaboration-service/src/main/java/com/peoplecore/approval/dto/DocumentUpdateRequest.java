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
public class DocumentUpdateRequest {

    private String docTitle;
    private String docData;
    private Boolean isEmergency;
    private Boolean isPublic; // 재기안 시 사용 (null 이면 이전 문서 값 유지) — 임시저장 수정 흐름에선 사용되지 않음
    private String docOpinion;
    private String htmlContent;     // 재기안 시점의 완성된 HTML

    /**
     * 결재선 수정
     */
    private List<DocumentCreateRequest.ApprovalLineRequest> approvalLines;
}
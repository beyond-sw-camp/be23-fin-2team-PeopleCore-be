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
    private String docOpinion;

    /**
     * 결재선 수정
     */
    private List<DocumentCreateRequest.ApprovalLineRequest> approvalLines;
}
package com.peoplecore.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentListResponseDto {
    private Long docId;
    private String docTitle;
    private String docNum;
    private String docStatus;
    private Boolean isEmergency;
    private Boolean isPublic;
    private String formName;
    private Long formId;        // 양식 PK — 클릭 시 양식 메타 조회용
    private String formCode;    // 양식 코드 — 분기/식별용
    private String drafterName;
    private String drafterDept;
    private LocalDateTime createdAt;
    private Boolean hasAttachment;
}

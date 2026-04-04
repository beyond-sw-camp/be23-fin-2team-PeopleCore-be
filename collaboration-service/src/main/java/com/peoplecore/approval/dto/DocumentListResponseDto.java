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
    private String formName;
    private String drafterName;
    private String drafterDept;
    private LocalDateTime createdAt;
    private Boolean hasAttachment;
}

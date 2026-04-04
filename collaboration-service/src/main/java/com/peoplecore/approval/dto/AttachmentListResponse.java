package com.peoplecore.approval.dto;

import com.peoplecore.approval.entity.ApprovalAttachment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttachmentListResponse {
    private Long attachId;
    private String fileName;
    private Long fileSize;

    public static AttachmentListResponse from(ApprovalAttachment attachment) {
        return AttachmentListResponse.builder()
                .attachId(attachment.getAttachId())
                .fileName(attachment.getFileName())
                .fileSize(attachment.getFileSize())
                .build();
    }
}

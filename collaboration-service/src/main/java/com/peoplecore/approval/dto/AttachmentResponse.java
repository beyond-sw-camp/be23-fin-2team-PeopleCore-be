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
public class AttachmentResponse {

    private Long attachId;
    private String fileName;
    private Long fileSize;
    private String fileUrl;

    public static AttachmentResponse from(ApprovalAttachment attachment, String fileUrl) {
        return AttachmentResponse.builder()
                .attachId(attachment.getAttachId())
                .fileName(attachment.getFileName())
                .fileSize(attachment.getFileSize())
                .fileUrl(fileUrl)
                .build();
    }
}

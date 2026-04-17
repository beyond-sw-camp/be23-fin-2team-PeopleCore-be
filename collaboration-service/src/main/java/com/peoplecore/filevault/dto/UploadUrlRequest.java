package com.peoplecore.filevault.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadUrlRequest {
    private Long folderId;
    private String fileName;
    private String mimeType;
    private Long sizeBytes;
}

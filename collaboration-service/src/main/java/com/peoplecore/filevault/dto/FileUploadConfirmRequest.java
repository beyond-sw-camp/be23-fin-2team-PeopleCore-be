package com.peoplecore.filevault.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileUploadConfirmRequest {
    private Long folderId;
    private String name;
    private String mimeType;
    private Long sizeBytes;
    private String storageKey;
}

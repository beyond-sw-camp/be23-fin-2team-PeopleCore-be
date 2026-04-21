package com.peoplecore.filevault.dto;

import com.peoplecore.filevault.entity.FileItem;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileResponse {
    private Long fileId;
    private Long folderId;
    private String name;
    private String mimeType;
    private Long sizeBytes;
    private Long uploadedBy;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
    /** 현재 사용자의 즐겨찾기 여부. 단건 응답이나 호출자가 모를 땐 false. */
    private boolean starred;

    public static FileResponse from(FileItem file) {
        return from(file, false);
    }

    public static FileResponse from(FileItem file, boolean starred) {
        return FileResponse.builder()
            .fileId(file.getId())
            .folderId(file.getFolderId())
            .name(file.getName())
            .mimeType(file.getMimeType())
            .sizeBytes(file.getSizeBytes())
            .uploadedBy(file.getUploadedBy())
            .createdAt(file.getCreatedAt())
            .deletedAt(file.getDeletedAt())
            .starred(starred)
            .build();
    }
}

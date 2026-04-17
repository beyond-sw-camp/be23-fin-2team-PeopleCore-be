package com.peoplecore.filevault.dto;

import com.peoplecore.filevault.entity.FileFolder;
import com.peoplecore.filevault.entity.FolderType;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FolderResponse {
    private Long folderId;
    private String name;
    private FolderType type;
    private Long parentFolderId;
    private Boolean isSystemDefault;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;

    public static FolderResponse from(FileFolder folder) {
        return FolderResponse.builder()
            .folderId(folder.getId())
            .name(folder.getName())
            .type(folder.getType())
            .parentFolderId(folder.getParentFolderId())
            .isSystemDefault(folder.getIsSystemDefault())
            .createdAt(folder.getCreatedAt())
            .deletedAt(folder.getDeletedAt())
            .build();
    }
}

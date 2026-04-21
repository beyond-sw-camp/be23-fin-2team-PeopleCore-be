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
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
    /** 현재 사용자의 즐겨찾기 여부. 단건 응답이나 호출자가 모를 땐 false. */
    private boolean starred;

    public static FolderResponse from(FileFolder folder) {
        return from(folder, false);
    }

    public static FolderResponse from(FileFolder folder, boolean starred) {
        return FolderResponse.builder()
            .folderId(folder.getId())
            .name(folder.getName())
            .type(folder.getType())
            .parentFolderId(folder.getParentFolderId())
            .isSystemDefault(folder.getIsSystemDefault())
            .createdBy(folder.getCreatedBy())
            .createdAt(folder.getCreatedAt())
            .deletedAt(folder.getDeletedAt())
            .starred(starred)
            .build();
    }
}

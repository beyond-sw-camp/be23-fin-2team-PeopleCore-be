package com.peoplecore.filevault.dto;

import com.peoplecore.filevault.entity.FolderType;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FolderCreateRequest {
    private String name;
    private FolderType type;
    private Long parentFolderId;
    private Long deptId;
}

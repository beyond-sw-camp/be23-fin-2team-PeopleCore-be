package com.peoplecore.approval.dto;


import com.peoplecore.approval.entity.ApprovalFormFolder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormFolderResponse {
    private Long folderId;
    private String folderName;
    private String folderPath;
    private Integer folderSortOrder;
    private Boolean folderIsVisible;
    private List<FormFolderResponse> children;

    public static FormFolderResponse from(ApprovalFormFolder folder) {
        return FormFolderResponse.builder()
                .folderId(folder.getFolderId())
                .folderName(folder.getFolderName())
                .folderPath(folder.getFolderPath())
                .folderSortOrder(folder.getFolderSortOrder())
                .folderIsVisible(folder.getFolderIsVisible())
                .children(new ArrayList<>())
                .build();
    }
}

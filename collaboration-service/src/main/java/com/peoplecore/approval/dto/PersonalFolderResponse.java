package com.peoplecore.approval.dto;


import com.peoplecore.approval.entity.PersonalApprovalFolder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalFolderResponse {
    private Long id;
    private String name;
    private LocalDate createdAt;
    private Integer sortOrder;
    private int docCount;

    public static PersonalFolderResponse from(PersonalApprovalFolder folder, int docCount) {
        return PersonalFolderResponse.builder()
                .id(folder.getPersonalFolderId())
                .name(folder.getFolderName())
                .createdAt(folder.getCreatedAt() != null ? folder.getCreatedAt().toLocalDate() : null)
                .sortOrder(folder.getSortOrder())
                .docCount(docCount)
                .build();
    }
}

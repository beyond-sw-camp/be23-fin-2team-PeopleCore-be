package com.peoplecore.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApprovalFormFolderCreateRequest {
    private String folderName;
    private Long parentId; // null이면 최상위 루트 폴더

}

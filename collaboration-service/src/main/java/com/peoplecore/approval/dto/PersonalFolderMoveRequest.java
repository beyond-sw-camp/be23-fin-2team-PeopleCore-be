package com.peoplecore.approval.dto;

import jakarta.annotation.security.DenyAll;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalFolderMoveRequest {
    /*이동할 문서 id 목록*/
    private List<Long> docIds;
    /*이동할 문서 id */
    private Long targetFolderId;
}

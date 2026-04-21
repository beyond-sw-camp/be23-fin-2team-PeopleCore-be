package com.peoplecore.filevault.permission.dto;

import lombok.*;

import java.util.List;

/**
 * 파일함 ACL 전체 응답 — Owner + 멤버 목록 (Owner 도 멤버에 포함).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileBoxAclResponse {
    private Long folderId;
    private String folderName;
    private FileBoxAclEntryResponse owner;
    private List<FileBoxAclEntryResponse> members;
}

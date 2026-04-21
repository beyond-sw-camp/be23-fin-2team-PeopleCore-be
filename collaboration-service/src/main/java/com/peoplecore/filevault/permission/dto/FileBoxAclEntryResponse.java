package com.peoplecore.filevault.permission.dto;

import lombok.*;

/**
 * 파일함 ACL 한 행 (사원 + 4-플래그 + 표시 메타).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileBoxAclEntryResponse {
    private Long empId;
    private String empName;
    private String deptName;
    private String gradeName;
    private String titleName;
    private boolean canRead;
    private boolean canWrite;
    private boolean canDownload;
    private boolean canDelete;
}

package com.peoplecore.approval.dto;

import com.peoplecore.approval.entity.DeptApprovalFolder;
import com.peoplecore.approval.entity.DeptFolderManager;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeptFolderResponse {

    private Long id;
    private String name;
    private LocalDate createdAt;
    private int docCount;
    private Integer sortOrder;
    private List<ManagerInfo> managers;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ManagerInfo {
        private Long empId;
        private String empName;
        private String deptName;
    }

    public static DeptFolderResponse from(DeptApprovalFolder folder, int docCount, List<DeptFolderManager> managers) {
        return DeptFolderResponse.builder()
                .id(folder.getDeptAppFolderId())
                .name(folder.getFolderName())
                .createdAt(folder.getCreatedAt() != null ? folder.getCreatedAt().toLocalDate() : null)
                .docCount(docCount)
                .sortOrder(folder.getSortOrder())
                .managers(managers.stream()
                        .map(m -> ManagerInfo.builder()
                                .empId(m.getEmpId())
                                .empName(m.getEmpName())
                                .deptName(m.getDeptName())
                                .build())
                        .toList())
                .build();
    }
}

package com.peoplecore.department.dto;

import com.peoplecore.department.domain.Department;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DepartmentResponse {

    private Long id;
    private Long parentDeptId;
    private String deptName;
    private String deptCode;
    private long memberCount;
    private List<DepartmentResponse> children;

    public static DepartmentResponse from(Department dept, long memberCount) {
        return DepartmentResponse.builder()
                .id(dept.getId())
                .parentDeptId(dept.getParentDeptId())
                .deptName(dept.getDeptName())
                .deptCode(dept.getDeptCode())
                .memberCount(memberCount)
                .children(List.of())
                .build();
    }

    public static DepartmentResponse withChildren(Department dept, long memberCount, List<DepartmentResponse> children) {
        return DepartmentResponse.builder()
                .id(dept.getId())
                .parentDeptId(dept.getParentDeptId())
                .deptName(dept.getDeptName())
                .deptCode(dept.getDeptCode())
                .memberCount(memberCount)
                .children(children)
                .build();
    }
}

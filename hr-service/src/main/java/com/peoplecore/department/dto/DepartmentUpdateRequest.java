package com.peoplecore.department.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DepartmentUpdateRequest {

    private String deptName;
    private String deptCode;
    private Long parentDeptId;
}

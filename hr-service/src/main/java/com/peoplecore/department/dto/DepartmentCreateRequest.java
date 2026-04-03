package com.peoplecore.department.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DepartmentCreateRequest {

    private Long parentDeptId;
    private String deptName;
    private String deptCode;
}

package com.peoplecore.department.dto;

import lombok.*;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class DepartmentCreateRequest {

    private Long parentDeptId;
    private String deptName;
    private String deptCode;
}

package com.peoplecore.department.dto;

import lombok.*;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class DepartmentUpdateRequest {

    private String deptName;
    private String deptCode;
    private Long parentDeptId;
}

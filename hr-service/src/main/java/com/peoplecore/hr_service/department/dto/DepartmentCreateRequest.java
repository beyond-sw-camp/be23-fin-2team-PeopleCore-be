package com.peoplecore.hr_service.department.dto;

import jakarta.annotation.security.DenyAll;
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

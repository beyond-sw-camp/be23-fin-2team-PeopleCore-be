package com.peoplecore.department.dto;

import com.peoplecore.department.domain.Department;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternalDeptResponseDto {
    private Long deptId;
    private Long parentDeptId;
    private String deptName;
    private String deptCode;

    public static InternalDeptResponseDto from(Department dept) {
        return InternalDeptResponseDto.builder()
                .deptId(dept.getDeptId())
                .parentDeptId(dept.getParentDeptId())
                .deptName(dept.getDeptName())
                .deptCode(dept.getDeptCode())
                .build();
    }
}

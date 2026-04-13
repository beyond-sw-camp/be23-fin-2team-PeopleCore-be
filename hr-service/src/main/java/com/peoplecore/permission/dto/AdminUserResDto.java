package com.peoplecore.permission.dto;

import com.peoplecore.employee.domain.EmpRole;
import com.peoplecore.employee.domain.Employee;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class AdminUserResDto {

    private Long empId;
    private String empName;
    private String empNum;
    private String deptName;
    private String gradeName;
    private EmpRole empRole;     // 현재 권한
    private String empEmail;

    // Employee 엔티티 -> 응답 DTO 변환
    public static AdminUserResDto fromEntity(Employee emp) {
        return AdminUserResDto.builder()
                .empId(emp.getEmpId())
                .empName(emp.getEmpName())
                .empNum(emp.getEmpNum())
                .deptName(emp.getDept() != null ? emp.getDept().getDeptName() : null)
                .gradeName(emp.getGrade() != null ? emp.getGrade().getGradeName() : null)
                .empRole(emp.getEmpRole())
                .empEmail(emp.getEmpEmail())
                .build();
    }
}

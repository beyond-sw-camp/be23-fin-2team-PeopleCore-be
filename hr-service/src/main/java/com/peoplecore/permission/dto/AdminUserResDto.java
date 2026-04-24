/*
package com.peoplecore.permission.dto;

import com.peoplecore.employee.domain.EmpRole;
import com.peoplecore.employee.domain.Employee;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private LocalDateTime grantedAt;  // SUPER_ADMIN 부여일

    // QueryDSL Projections.constructor용 (grantedAt 없는 버전)
    public AdminUserResDto(Long empId, String empName, String empNum,
                           String deptName, String gradeName,
                           EmpRole empRole, String empEmail) {
        this.empId = empId;
        this.empName = empName;
        this.empNum = empNum;
        this.deptName = deptName;
        this.gradeName = gradeName;
        this.empRole = empRole;
        this.empEmail = empEmail;
    }

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
*/

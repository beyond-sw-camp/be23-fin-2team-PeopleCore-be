package com.peoplecore.permission.dto;


import com.peoplecore.employee.domain.EmpRole;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.permission.domain.Permission;
import com.peoplecore.permission.domain.PermissionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class PermissionReqDto {

    private Long empId;              // 대상 사원 ID
    private EmpRole requestedRole;   // 요청 권한(변경할 권한)
    private String reason;           // 사유

    // 요청 DTO -> Permission 엔티티 변환 (권한 부여)
    public Permission toEntity(Employee employee) {
        return Permission.builder()
                .employee(employee)
                .empName(employee.getEmpName())
                .requestedRole(this.requestedRole)
                .currentRole(employee.getEmpRole())
                .status(PermissionStatus.GRANTED)
                .reason(this.reason)
                .createdAt(LocalDateTime.now())
                .build();
    }
}

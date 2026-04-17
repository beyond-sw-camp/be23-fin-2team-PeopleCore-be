package com.peoplecore.permission.dto;

import com.peoplecore.employee.domain.EmpRole;
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
public class PermissionHistoryResDto {

    private Long permissionId;
    private Long empId;
    private String empName;          // 대상 사원
    private String empNum;
    private EmpRole requestedRole;   // 변경된 권한
    private EmpRole currentRole;     // 변경 전 권한
    private PermissionStatus status; // GRANTED / REVOKED
    private String actorName;        // 수행자 (부여자 또는 회수자)
    private LocalDateTime processedAt;

    public static PermissionHistoryResDto fromEntity(Permission p) {
        return PermissionHistoryResDto.builder()
                .permissionId(p.getPermissionId())
                .empId(p.getEmployee().getEmpId())
                .empName(p.getEmployee().getEmpName())
                .empNum(p.getEmployee().getEmpNum())
                .requestedRole(p.getRequestedRole())
                .currentRole(p.getCurrentRole())
                .status(p.getStatus())
                .actorName(p.getGrantor() != null ? p.getGrantor().getEmpName() : null)
                .processedAt(p.getProcessedAt())
                .build();
    }
}

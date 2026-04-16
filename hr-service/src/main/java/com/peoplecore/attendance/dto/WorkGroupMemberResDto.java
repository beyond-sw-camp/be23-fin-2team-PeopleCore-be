package com.peoplecore.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.peoplecore.employee.domain.Employee;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkGroupMemberResDto {

    /**
     * 사원 ID
     */
    private Long empId;

    /**
     * 사원 이름
     */
    private String empName;

    /**
     * 부서명 (부서 없을 수 있음 → nullable)
     */
    private String deptName;

    /**
     * 직급명 (직급 없을 수 있음 → nullable)
     */
    private String gradeName;

    /**
     * 직책명 (직책 없을 수 있음 → nullable)
     */
    private String titleName;
    /**
     * 근무 그룹 배정 일시 (nullable)
     */
    private LocalDateTime assignedAt;

    /** 근무 그룹에 속한 직원들  리스트 반환*/
    public static WorkGroupMemberResDto from(Employee emp) {
        return WorkGroupMemberResDto.builder()
                .empId(emp.getEmpId())
                .empName(emp.getEmpName())
                .deptName(emp.getDept() != null ? emp.getDept().getDeptName() : null)
                .gradeName(emp.getGrade() != null ? emp.getGrade().getGradeName() : null)
                .titleName(emp.getTitle() != null ? emp.getTitle().getTitleName() : null)
                .assignedAt(emp.getWorkGroupAssignedAt())
                .build();
    }
}
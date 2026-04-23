package com.peoplecore.evaluation.dto;

import lombok.*;

// 11번 - 미제출·미산정 직원 목록 항목 (finalGrade IS NULL 대상)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UnassignedEmployeeDto {
    private Long empId;        // 확정 시 acknowledgedEmpIds 로 돌려받기 위한 식별자
    private String empNum;     // 사번
    private String empName;    // 이름
    private String deptName;   // 부서 (시즌 오픈 시 스냅샷)
    private String position;   // 직급 (시즌 오픈 시 스냅샷)
}

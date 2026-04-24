package com.peoplecore.evaluation.dto;

import lombok.*;

// preview 용 — 한 부서의 후보 사원 1명
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeptCandidateDto {
    private Long empId;
    private String empName;
}

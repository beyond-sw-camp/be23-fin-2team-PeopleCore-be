package com.peoplecore.evaluation.dto;

import lombok.*;

// conflict 부서에서 HR 이 선택한 평가자 (deptId → empId)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeptOverrideDto {
    private Long deptId;
    private Long empId;
}

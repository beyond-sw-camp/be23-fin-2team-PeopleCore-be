package com.peoplecore.evaluation.dto;

import lombok.*;

import java.util.List;

// preview 용 — 부서 하나의 매칭 결과. conflict=true 면 HR 이 1명 지정 필요.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeptResolutionDto {
    private Long deptId;
    private String deptName;
    private List<DeptCandidateDto> candidates;
    private boolean conflict;   // candidates.size() >= 2
}

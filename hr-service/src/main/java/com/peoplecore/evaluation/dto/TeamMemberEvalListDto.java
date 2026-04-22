package com.peoplecore.evaluation.dto;

import lombok.*;

// 팀원 평가 - 팀장 화면 좌측 팀원 목록 항목
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TeamMemberEvalListDto {
    private Long empId;
    private String name;
    private String dept;
    private String position;
    private int kpiCount;                    // 승인된 KPI 수
    private int okrCount;                    // 승인된 OKR 수
    private boolean selfEvalSubmitted;       // 대상 사원의 자기평가 제출 여부
    private boolean managerEvalSubmitted;    // 본인(팀장)이 이 팀원 평가 제출했는지 (목록 뱃지)
}

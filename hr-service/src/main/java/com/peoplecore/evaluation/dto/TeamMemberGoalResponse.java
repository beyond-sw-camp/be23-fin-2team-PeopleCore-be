package com.peoplecore.evaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

// 팀원 1명 + 그 사원의 목표 목록 (팀장 승인 화면용)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TeamMemberGoalResponse {
    private Long id;                        // empId
    private String employeeName;
    private String dept;                    // 부서명
    private String position;                // 직급(Grade) 또는 직책(Title)
    private LocalDateTime submittedDate;    // 이 팀원이 가장 마지막으로 목표를 제출한 시각 (가장 늦은 submittedAt)
    private List<GoalResponse> goals;
}

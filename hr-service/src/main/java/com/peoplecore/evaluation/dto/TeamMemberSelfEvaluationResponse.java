package com.peoplecore.evaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

// 팀원 1명 + 그 사원의 자기평가 목록 (팀장 검토 화면용)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TeamMemberSelfEvaluationResponse {
    private Long id;                              // empId
    private String employeeName;
    private String dept;                          // 부서명
    private String position;                      // 직급(Grade) 또는 직책(Title)
    private LocalDateTime submittedDate;          // 가장 늦은 submittedAt (미제출 팀원은 null)
    private List<SelfEvaluationResponse> evaluations;
}

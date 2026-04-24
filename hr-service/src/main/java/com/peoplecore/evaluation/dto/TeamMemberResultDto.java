package com.peoplecore.evaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 팀장 - 팀원 최종 평가결과
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TeamMemberResultDto {
    private Long empId;
    private String empName;           // Employee 현재값 (개명/오타 반영)
    private String position;          // EvalGrade.positionSnapshot - 시즌 당시 직급

    private String managerGradeId;    // 팀장이 부여한 등급 (ManagerEvaluation.gradeLabel)
    private String autoGradeId;       // 자동 산정 등급 (EvalGrade.autoGrade)
    private String finalGradeId;      // 최종 확정 등급 (EvalGrade.finalGrade)

    private String managerComment;    // 내부용 - 카드 미리보기 + 모달 전체 (사원 비공개)
    private String managerFeedback;   // 사원 공개 피드백 - 모달 전용
}

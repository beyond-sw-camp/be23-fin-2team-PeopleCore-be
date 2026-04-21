package com.peoplecore.evaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 팀장 편향 보정(Z-score) 효과 요약 — 자동 산정 화면 차트용
// 팀장 점수(managerScore / managerScoreAdjusted) 기준으로 팀별 평균을 집계해 보정 전/후 비교
// minTeamSize 미만 또는 originalAvg == adjustedAvg 인 팀은 프론트에서 "제외" 배지로 처리
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TeamBiasResponseDto {
    private Integer minTeamSize;   // 회사 규칙의 최소 팀 크기 — 미만이면 Z-score 보정 제외
    private List<Team> teams;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Team {
        private Long deptId;
        private String deptName;
        private Long memberCount;    // 팀장 점수 있는 사원 수
        private Double originalAvg;  // 보정 전 팀장 점수 평균 (managerScore)
        private Double adjustedAvg;  // Z-score 보정 후 팀장 점수 평균 (managerScoreAdjusted)
    }
}

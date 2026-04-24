package com.peoplecore.evaluation.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;


// 평가 규칙 JSON 스냅샷 역직렬화용
// Season.formSnapshot 에 박제되는 병합 JSON 을 파싱 — 하드 컬럼 값 + formValues 섹션 통합
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FormSnapshotDto {
    // ─── formValues 섹션 ───
    private List<Item> itemList;             // 가중치설정 목록
    private List<Adjustment> adjustments;    // 근태/징계 등 가감점
    private List<GradeRule> gradeRules;      // 등급정의 (S/A/B/C/D)
    private List<RawScore> rawScoreTable;    // 팀장등급 → 원점수 환산
    private KpiScoring kpiScoring;           // KPI 달성률 환산

    // ─── 하드 컬럼 스냅샷 (시즌 OPEN 시점 값) ───
    private Integer taskWeightSang;          // 상 난이도 배수
    private Integer taskWeightJung;          // 중 난이도 배수
    private Integer taskWeightHa;            // 하 난이도 배수
    private Boolean useBiasAdjustment;       // 편향보정 사용 여부
    private BigDecimal biasWeight;           // 편향보정 강도
    private Integer minTeamSize;             // 편향보정 소규모 팀 판정 기준 인원


    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String id;              // 항목 식별자 (self/manager는 시스템 고정)
        private String name;            // 표시명
        private BigDecimal weight;      // 가중치 %
        private Boolean locked;         // true면 시스템 고정 항목 (자기평가/상위자평가) - 삭제/이름변경 불가
        private Boolean enabled;        // 고정 항목 사용 여부 (null/true면 사용, false면 점수 집계에서 제외)
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Adjustment {
        private String id;
        private String name;
        private BigDecimal points; //가감점수
        private Boolean enabled; //사용여부
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GradeRule {
        private String id; //rawScoreTable/ManagerEval 연결 키
        private String label; //표시명(s,a)
        private BigDecimal ratio; // 강제배분%
        private String  color;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RawScore {
        private String gradeId; //GradeRule.id
        private BigDecimal rawScore; // 환산 원점수
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KpiScoring {
        private BigDecimal cap; //달성률 상한
        private BigDecimal scaleTo; // 환산 만점
        private BigDecimal maintainTolerance;         // MAINTAIN ±n% 이내 만점 처리 (0이면 선형)
        private BigDecimal underperformanceThreshold; // 미달 기준 % (0이면 비활성)
        private BigDecimal underperformanceFactor;    // 미달 구간 점수 배율 (기본 1.0)
    }

}

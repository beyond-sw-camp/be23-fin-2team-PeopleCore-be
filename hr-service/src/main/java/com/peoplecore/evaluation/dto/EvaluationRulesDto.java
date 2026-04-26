package com.peoplecore.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.evaluation.domain.EvaluationRules;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

// 시즌에 적용된 평가 규칙 DTO
// EvaluationRules 엔티티의 컬럼 값 + formSnapshot(JSON) 파싱 결과를 합쳐서 내려줌
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvaluationRulesDto {
    private List<EvalItem> items;                  // 평가항목 (자기평가/상위자평가) — JSON에서 파싱
    private List<GradeItem> grades;                // 등급 체계 (S/A/B/C/D) — JSON에서 파싱
    private List<AdjustItem> adjustments;          // 가감점 항목 (근태/징계/표창) — JSON에서 파싱
    private List<GradeRawScoreItem> rawScoreTable; // 등급 원점수 변환표 — JSON에서 파싱, grades.id 참조
    private TaskGradeWeight taskGradeWeights;      // 업무등급(상/중/하) 가중 배수 — 엔티티 컬럼 3개 조합
    private KpiScoringConfig kpiScoring;           // KPI 점수 환산 규칙 — JSON에서 파싱
    private Boolean useBiasAdjustment;             // 팀장 편향 보정 사용 여부 — 엔티티 컬럼
    private BigDecimal biasWeight;                 // 편향 보정 강도 — 엔티티 컬럼
    private Integer minTeamSize;                   // 최소 팀 인원 — 엔티티 컬럼
    private Long formVersion;                      // 폼 버전 (스냅샷 뜰 때마다 ++) — 엔티티 컬럼


    // 평가항목 (예: {id:"self", name:"자기평가", weight:30})
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EvalItem {
        private String id;         // 식별자
        private String name;       // 항목명
        private Integer weight;    // 가중치 %
        private Boolean locked;    // true = 삭제/이름변경 불가한 시스템 필수 항목
        private Boolean enabled;   // locked 항목의 ON/OFF (false = 이번 시즌은 건너뜀)
    }


    // 등급 (예: {id:"S", label:"S", ratio:10, color:"#7c3aed"})
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GradeItem {
        private String id;         // 등급 식별자
        private String label;      // 표시 라벨 (S/A/B…)
        private Integer ratio;     // 강제배분 목표 비율 %
        private String color;      // UI 색상 (#hex)
    }


    // 등급 원점수 변환표 (예: {gradeId:"S", rawScore:95})
    // 팀장이 등급 부여 시 이 표에 따라 managerScore로 환산
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GradeRawScoreItem {
        private String gradeId;    // GradeItem.id 참조
        private Integer rawScore;  // 원점수 (0~100)
    }


    // 가감점 (예: {id:"attendance", name:"근태 감점", points:-2, threshold:0, enabled:true})
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdjustItem {
        private String id;         // 항목 식별자
        private String name;       // 항목명
        private Integer points;    // 점수 (음수=감점 / 양수=가산)
        private Integer threshold; // 면제 횟수 - 이 횟수까지는 무감점, 초과분에만 points 적용 (null/0이면 면제 없음)
        private Boolean enabled;   // 활성 여부
    }


    // 업무등급 가중 배수 (예: {상:3, 중:2, 하:1})
    // 목표 난이도(상/중/하)에 따라 점수 반영 비중을 다르게 줄 때 사용
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TaskGradeWeight {
        private Integer 상;   // 상 난이도 배수
        private Integer 중;   // 중 난이도 배수
        private Integer 하;   // 하 난이도 배수
    }


    // KPI 점수 환산 규칙 (예: {cap:120, scaleTo:100, maintainTolerance:0, ...})
    // 달성률 → 점수 변환 시 적용되는 상한·리스케일·MAINTAIN 허용오차·미달 패널티
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KpiScoringConfig {
        private Integer cap;                       // 점수 상한 % (기본 120)
        private Integer scaleTo;                   // 리스케일 만점 (기본 100)
        private Integer maintainTolerance;         // MAINTAIN 허용 ±n% (기본 0 = 선형)
        private Integer underperformanceThreshold; // 미달 기준 % (기본 0 = 비활성)
        private BigDecimal underperformanceFactor; // 미달 구간 점수 배율 (기본 1.0)
    }


    // JSON 파싱 전용 내부 타입 (저장 JSON 키: itemList/gradeRules/...)
    // merged snapshot JSON 의 하드 컬럼 6종 키도 함께 파싱
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class FormSnapshot {
        private List<EvalItem> itemList;
        private List<GradeItem> gradeRules;
        private List<AdjustItem> adjustments;
        private List<GradeRawScoreItem> rawScoreTable;
        private KpiScoringConfig kpiScoring;
        // merged snapshot 전용 (회사 규칙 formValues JSON 엔 없음)
        private Integer taskWeightSang;
        private Integer taskWeightJung;
        private Integer taskWeightHa;
        private Boolean useBiasAdjustment;
        private BigDecimal biasWeight;
        private Integer minTeamSize;
    }


    // 회사 규칙 Entity -> DTO (GET /eval/rules 용 — 현재 편집 가능한 회사 규칙)
    // r      : EvaluationRules 엔티티 (null이면 규칙 미설정)
    // mapper : Spring 관리 ObjectMapper (파라미터로만)
    public static EvaluationRulesDto from(EvaluationRules r, ObjectMapper mapper) {
        if (r == null) return null;   // 규칙 row 자체가 없으면 null 반환

        // 회사 규칙의 현재 편집값(formValues) 을 파싱
        FormSnapshot snap = parse(r.getFormValues(), mapper);

        return buildDto(snap,
                r.getTaskWeightSang(), r.getTaskWeightJung(), r.getTaskWeightHa(),
                r.getUseBiasAdjustment(), r.getBiasWeight(), r.getMinTeamSize(),
                r.getFormVersion());
    }

    // 시즌 스냅샷 JSON -> DTO (SeasonDetail 용 — 그 시즌이 박제한 규칙)
    //   mergedJson  : Season.formSnapshot (시즌 OPEN 시 동결된 병합 JSON — formValues 섹션 + 하드 컬럼 6종)
    //   formVersion : Season.formVersion (박제 당시 회사 규칙 버전)
    public static EvaluationRulesDto fromSnapshot(String mergedJson, Long formVersion, ObjectMapper mapper) {
        if (mergedJson == null) return null;

        FormSnapshot snap = parse(mergedJson, mapper);

        // 하드 컬럼 값은 병합 JSON 안에 그대로 박제돼 있음 — snap 에서 바로 꺼냄
        return buildDto(snap,
                snap.taskWeightSang, snap.taskWeightJung, snap.taskWeightHa,
                snap.useBiasAdjustment, snap.biasWeight, snap.minTeamSize,
                formVersion);
    }

    // 공통 조립부 — JSON 파싱 결과 + 하드 컬럼 값으로 DTO 구성
    private static EvaluationRulesDto buildDto(FormSnapshot snap,
                                               Integer sang, Integer jung, Integer ha,
                                               Boolean useBias, BigDecimal biasW, Integer minTeam,
                                               Long formVersion) {
        return EvaluationRulesDto.builder()
                // JSON 필드가 누락돼도 null 대신 빈 리스트로 방어 (FE가 .map() 안 터지게)
                .items(snap.itemList != null ? snap.itemList : Collections.emptyList())
                .grades(snap.gradeRules != null ? snap.gradeRules : Collections.emptyList())
                .adjustments(snap.adjustments != null ? snap.adjustments : Collections.emptyList())
                .rawScoreTable(snap.rawScoreTable != null ? snap.rawScoreTable : Collections.emptyList())
                .taskGradeWeights(TaskGradeWeight.builder()
                        .상(sang)
                        .중(jung)
                        .하(ha)
                        .build())
                // KPI 환산 규칙 — JSON에 없으면 기본값(120/100/0/0/1.0)으로 fallback
                .kpiScoring(snap.kpiScoring != null ? snap.kpiScoring : KpiScoringConfig.builder()
                        .cap(120)
                        .scaleTo(100)
                        .maintainTolerance(0)
                        .underperformanceThreshold(0)
                        .underperformanceFactor(BigDecimal.ONE)
                        .build())
                .useBiasAdjustment(useBias)
                .biasWeight(biasW)
                .minTeamSize(minTeam)
                .formVersion(formVersion)
                .build();
    }


    // JSON 문자열 -> FormSnapshot 파싱 (예외/빈 값 방어)
    private static FormSnapshot parse(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) return new FormSnapshot();   // 값 없으면 빈 객체
        try {
            return mapper.readValue(json, FormSnapshot.class);
        } catch (JsonProcessingException e) {
            // 파싱 실패해도 상세 조회 자체는 성공하도록 빈 객체 반환
            // (500 에러로 이어지면 FE 상세 화면 전체가 뜨지 않음)
            return new FormSnapshot();
        }
    }
}

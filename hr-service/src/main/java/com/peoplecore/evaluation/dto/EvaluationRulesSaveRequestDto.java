package com.peoplecore.evaluation.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EvaluationRulesSaveRequestDto {
    private List<EvaluationRulesDto.EvalItem> itemList; //평가항목
    private List<EvaluationRulesDto.GradeItem> grades; // 등급체계
    private List<EvaluationRulesDto.AdjustItem>adjustItems; // 가감점
    private List<EvaluationRulesDto.GradeRawScoreItem>gradeItems;  //등급 -> 원점수 변환표
    private EvaluationRulesDto.KpiScoringConfig kpiScoringConfig; // kpi달성률 -> 점수 환산 규칙

//    하드로 박는 컬럼
    private EvaluationRulesDto.TaskGradeWeight taskGradeWeight; //업무등급
    private Boolean useBiasAdjustment; //팀장 편향 보정 사용여부
    private BigDecimal biasWeight; // 편향보정 강도(0~1)
    private Integer minTeamSize; //강제배분 최소 팀 인원



}

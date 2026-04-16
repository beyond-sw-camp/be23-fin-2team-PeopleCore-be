package com.peoplecore.evaluation.domain;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

//평가 규칙 - 시즌별 평가 규칙 설정 (Season과 1:1)
@Entity
@Table(name = "evaluation_rules")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationRules extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rules_id")
    private Long rulesId; // 규칙 PK

    // 대상 시즌 (시즌당 규칙 1세트)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    // ─────────────────────────────────────────────
    //  업무등급 배수 (상/중/하) — 목표 난이도에 따른 가중치 승수
    //    자기평가 점수 산정 시 목표별 비중 재정규화에 사용
    //    (예: 상=3, 중=2, 하=1 → 상 목표 하나가 하 목표 3배 영향)
    // ─────────────────────────────────────────────
    @Column(name = "task_weight_sang")
    @Builder.Default
    private Integer taskWeightSang = 3;

    @Column(name = "task_weight_jung")
    @Builder.Default
    private Integer taskWeightJung = 2;

    @Column(name = "task_weight_ha")
    @Builder.Default
    private Integer taskWeightHa = 1;

    // ─────────────────────────────────────────────
    // 팀장 편향 보정 설정 — 강제배분 단계에서 팀 간 점수 편차 보정에 사용
    // ─────────────────────────────────────────────
    @Column(name = "use_bias_adjustment")
    @Builder.Default
    private Boolean useBiasAdjustment = true; // 편향 보정 사용 여부

    @Column(name = "bias_weight", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal biasWeight = new BigDecimal("1.0"); // 편향 보정 강도 (0~1)

    @Column(name = "min_team_size")
    @Builder.Default
    private Integer minTeamSize = 5; // 최소 팀 인원 (미달 시 전사 fallback)

    // ─────────────────────────────────────────────
    // 동적 규칙 묶음 (JSON) — 섹션 ①②③④⑤를 한 JSON 오브젝트로 보관
    //   {
    //     "items":       [ {id,name,weight}, ... ],               // 자기:상위자 가중치
    //     "adjustments": [ {id,name,points,enabled}, ... ],       // 근태/징계/표창 가감점
    //     "grades":      [ {id,label,minScore,ratio,color}, ... ],// S/A/B/C/D 체계
    //     "rawScoreTable":[ {gradeId,rawScore}, ... ],            // 팀장 등급→원점수
    //     "kpiScoring":  {cap,scaleTo,maintainTolerance,...}      // KPI 달성률→점수 환산
    //   }
    // ─────────────────────────────────────────────

    // 현재 입력값 (DRAFT 중 수정 가능)
    @Column(name = "form_values", columnDefinition = "JSON")
    private String formValues;

    // 시즌 OPEN 시점의 formValues 스냅샷 (이후 불변)
    // 모든 평가 산정 로직은 이 값을 기준으로 동작함
    @Column(name = "form_snapshot", columnDefinition = "JSON")
    private String formSnapshot;

    // 폼 버전 (스냅샷 뜰 때마다 증가) — 감사 추적용
    @Column(name = "form_version")
    private Long formVersion;

    // DRAFT 상태에서 규칙 수정 — 하드 컬럼 + formValues JSON 동시 갱신
    public void updateDraft(Integer taskWeightSang, //null일시 기본값
                            Integer taskWeightJung,
                            Integer taskWeightHa,
                            Boolean useBiasAdjustment,  //편향 보정 사용여부
                            BigDecimal biasWeight,  //편향 보정강도(0~1)
                            Integer minTeamSize,    //최소 팀인원(미달 시 전사 fallback)
                            String formValues) { //items/grades/adjusments/rawScoreTable/kpiScoring 직렬화 Json
        this.taskWeightSang = taskWeightSang;
        this.taskWeightJung = taskWeightJung;
        this.taskWeightHa = taskWeightHa;
        this.useBiasAdjustment = useBiasAdjustment;
        this.biasWeight = biasWeight;
        this.minTeamSize = minTeamSize;
        this.formValues = formValues;

    }

//    시즌 open시점 formValues를 formSnapshot으로 복사하여 불변 동결
    public void freezeSnapshot(){
        this.formSnapshot = this.formValues; // 동결본 복사
        this.formVersion = this.formVersion == null? 1: this.formVersion + 1L; //버전증가(null이면 1로 초기화)
    }


}

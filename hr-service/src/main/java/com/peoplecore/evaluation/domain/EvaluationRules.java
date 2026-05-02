package com.peoplecore.evaluation.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

// 평가 규칙 - 회사별 전사 공통 규칙
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

    // 대상 회사 (회사당 규칙 1세트)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

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
    //   { "items":..., "grades":..., "adjustments":..., "rawScoreTable":..., "kpiScoring":... }
    // ─────────────────────────────────────────────
    @Column(name = "form_values", columnDefinition = "JSON")
    private String formValues;

    // 규칙 버전 — 수정할 때마다 ++ (Season.formVersion 과 매칭되어 "이 시즌은 회사규칙 vN 시점" 추적)
    @Column(name = "form_version")
    @Builder.Default
    private Long formVersion = 0L;

    // 규칙 수정 — 하드 컬럼 + formValues JSON 동시 갱신, 버전 ++
    public void updateRules(Boolean useBiasAdjustment,
                            BigDecimal biasWeight,
                            Integer minTeamSize,
                            String formValues) {
        this.useBiasAdjustment = useBiasAdjustment;
        this.biasWeight = biasWeight;
        this.minTeamSize = minTeamSize;
        this.formValues = formValues;
        this.formVersion = this.formVersion == null ? 1L : this.formVersion + 1L;
    }
}

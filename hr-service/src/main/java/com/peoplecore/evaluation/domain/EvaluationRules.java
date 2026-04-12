package com.peoplecore.evaluation.domain;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

// 평가규칙 - 시즌별 평가 규칙 설정 (1:1)
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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season; // 대상 시즌

    @Column(name = "task_weight_sang")
    @Builder.Default
    private Integer taskWeightSang = 3; // 난이도 상 가중치

    @Column(name = "task_weight_jung")
    @Builder.Default
    private Integer taskWeightJung = 2; // 난이도 중 가중치

    @Column(name = "task_weight_ha")
    @Builder.Default
    private Integer taskWeightHa = 1; // 난이도 하 가중치

    @Column(name = "use_bias_adjustment")
    @Builder.Default
    private Boolean useBiasAdjustment = true; // 편향 보정 사용 여부

    @Column(name = "bias_weight", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal biasWeight = new BigDecimal("1.0"); // 편향 보정 가중치

    @Column(name = "min_team_size")
    @Builder.Default
    private Integer minTeamSize = 5; // 최소 팀 인원
}

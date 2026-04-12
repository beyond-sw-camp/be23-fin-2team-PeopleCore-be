package com.peoplecore.evaluation.domain;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

// 가감항목 - 근태감점/징계/표창가산 등
@Entity
@Table(name = "eval_rule_adjustment")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvalRuleAdjustment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "adj_id")
    private Long adjId; // 가감항목 PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rules_id", nullable = false)
    private EvaluationRules rules; // 소속 규칙

    @Column(name = "name", length = 50)
    private String name; // 항목명 (근태감점/징계/표창가산)

    @Column(name = "points")
    private Integer points; // 점수 (음수=감점, 양수=가산)

    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = true; // 활성 여부
}

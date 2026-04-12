package com.peoplecore.evaluation.domain;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

// 등급정의 - S/A/B/C/D 등급별 점수구간 및 강제배분 비율
@Entity
@Table(name = "eval_rule_grade")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvalRuleGrade extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "grade_def_id")
    private Long gradeDefId; // 등급정의 PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rules_id", nullable = false)
    private EvaluationRules rules; // 소속 규칙

    @Column(name = "label", length = 10)
    private String label; // 등급 라벨 (S/A/B/C/D)

    @Column(name = "min_score")
    private Integer minScore; // 최저 점수

    @Column(name = "ratio")
    private Integer ratio; // 강제배분 비율(%)

    @Column(name = "order_no")
    private Integer orderNo; // 순서

    @Column(name = "color", length = 10)
    private String color; // 표시 색상
}

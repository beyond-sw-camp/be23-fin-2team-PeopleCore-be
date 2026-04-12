package com.peoplecore.evaluation.domain;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

// 평가항목 - 자기평가/상위자평가/동료평가 등 항목별 가중치
@Entity
@Table(name = "eval_rule_item")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvalRuleItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long itemId; // 항목 PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rules_id", nullable = false)
    private EvaluationRules rules; // 소속 규칙

    @Column(name = "name", length = 50)
    private String name; // 항목명 (자기평가/상위자평가/동료평가)

    @Column(name = "weight")
    private Integer weight; // 가중치(%)
}

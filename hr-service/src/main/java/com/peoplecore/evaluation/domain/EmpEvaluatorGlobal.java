package com.peoplecore.evaluation.domain;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

// 회사 단위 글로벌 사원-평가자 매핑. HR이 상시 유지, 시즌 OPEN 시 EvalGrade 의 evaluator snapshot 으로 박제됨.
// excluded=true 면 그 사원은 평가 대상에서 제외 (evaluator null). false 면 evaluator 필수.
@Entity
@Table(name = "emp_evaluator_global")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmpEvaluatorGlobal extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluatee_emp_id", nullable = false)
    private Employee evaluatee;  // 피평가자

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluator_emp_id")
    private Employee evaluator;  // 평가자 — excluded=true 면 null

    @Column(name = "is_excluded", nullable = false)
    private boolean excluded;  // 평가 제외 여부 (true 면 그 시즌에 평가 안 함)

    // 평가 제외 모드로 전환 — evaluator null
    public void markExcluded() {
        this.evaluator = null;
        this.excluded = true;
    }
}

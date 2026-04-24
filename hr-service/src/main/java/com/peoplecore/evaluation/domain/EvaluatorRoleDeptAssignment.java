package com.peoplecore.evaluation.domain;

import com.peoplecore.department.domain.Department;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

// 부서별 실제 평가자. 1 부서 = 1 평가자.
@Entity
@Table(
    name = "evaluator_role_dept_assignment",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_evaluator_assignment_config_dept",
        columnNames = {"config_id", "dept_id"}
    )
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluatorRoleDeptAssignment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    private Long id;

    // 소속 config
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_evaluator_assignment_config"))
    private EvaluatorRoleConfig config;

    // 대상 부서
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dept_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_evaluator_assignment_dept"))
    private Department dept;

    // 그 부서의 평가자 사원
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_evaluator_assignment_emp"))
    private Employee employee;
}

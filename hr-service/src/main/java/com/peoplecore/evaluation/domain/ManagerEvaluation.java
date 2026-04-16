package com.peoplecore.evaluation.domain;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 팀장평가 - 상위자(팀장)가 사원에게 부여하는 평가
@Entity
@Table(name = "manager_evaluation")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManagerEvaluation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mgr_eval_id")
    private Long mgrEvalId; // 팀장평가 PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee; // 피평가자

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluator_id")
    private Employee evaluator; // 평가자(팀장)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id")
    private Season season; // 시즌

    @Column(name = "grade_label", length = 5)
    private String gradeLabel; // 부여 등급 (S/A/B/C/D)

    @Column(name = "comment", length = 1000)
    private String comment; // 평가 코멘트

    @Column(name = "feedback", length = 1000)
    private String feedback; // 피드백

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt; // 제출 시각
}

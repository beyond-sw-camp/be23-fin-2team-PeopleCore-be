package com.peoplecore.evaluation.domain;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 목표 - 사원별 시즌 목표 (KPI/OKR)
@Entity
@Table(name = "goal")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Goal extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "goal_id")
    private Long goalId; // 목표 PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee emp; // 대상 사원

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season; // 시즌

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kpi_id")
    private KpiTemplate kpiTemplate; // KPI 템플릿 (OKR이면 NULL)

    @Column(name = "goal_type", length = 10)
    private String goalType; // 목표 유형 (KPI/OKR)

    @Column(name = "category", length = 30)
    private String category; // 카테고리

    @Column(name = "title", length = 200)
    private String title; // 제목

    @Column(name = "description", length = 500)
    private String description; // 설명

    @Enumerated(EnumType.STRING)
    @Column(name = "task_grade")
    private TaskGrade taskGrade; // 업무 난이도 (상/중/하)

    @Column(name = "target_value", precision = 12, scale = 2)
    private BigDecimal targetValue; // 목표값 (KPI만)

    @Column(name = "target_unit", length = 10)
    private String targetUnit; // 목표 단위

    @Column(name = "approval_status", length = 20)
    @Builder.Default
    private String approvalStatus = "대기"; // 승인 상태 (대기/승인/반려)

    @Column(name = "reject_reason", length = 500)
    private String rejectReason; // 반려 사유

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt; // 제출 시각
}

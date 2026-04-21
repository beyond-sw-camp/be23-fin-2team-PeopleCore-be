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

    @Enumerated(EnumType.STRING)
    @Column(name = "goal_type", length = 10)
    private GoalType goalType; // 목표 유형 (KPI/OKR)

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

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", length = 20)
    @Builder.Default
    private GoalApprovalStatus approvalStatus = GoalApprovalStatus.DRAFT; // 상태 (작성중/대기/승인/반려)

    @Column(name = "reject_reason", length = 500)
    private String rejectReason; // 반려 사유

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt; // 제출 시각

    // KPI 목표 수정 - 템플릿에서 필드 자동 복사
    public void updateAsKpi(KpiTemplate template, BigDecimal targetValue, TaskGrade grade) {
        this.kpiTemplate = template;
        this.goalType = GoalType.KPI;
        this.category = template.getCategory().getOptionValue();
        this.title = template.getName();
        this.description = template.getDescription();
        this.targetUnit = template.getUnit().getOptionValue();
        this.targetValue = targetValue;
        this.taskGrade = grade;
    }

    // OKR 목표 수정 - 사원 입력값 그대로, KPI 관련 필드는 NULL 처리
    public void updateAsOkr(String category, String title, String description, TaskGrade grade) {
        this.kpiTemplate = null;
        this.goalType = GoalType.OKR;
        this.category = category;
        this.title = title;
        this.description = description;
        this.taskGrade = grade;
        this.targetValue = null;
        this.targetUnit = null;
    }

    // 반려된 목표 재수정 시 작성중 상태로 리셋 (재제출 가능)
    public void resetToDraft() {
        this.approvalStatus = GoalApprovalStatus.DRAFT;
        this.submittedAt = null;
        this.rejectReason = null;
    }

    // 목표 제출 - 작성중/반려 상태에서 대기 로 전환
    public void submit() {
        this.submittedAt = LocalDateTime.now();
        this.approvalStatus = GoalApprovalStatus.PENDING;
        this.rejectReason = null;   // 이전 반려 사유 초기화
    }

    // 팀장 승인 - 대기 상태만 승인 가능, 반려 사유 초기화
    public void approve() {
        this.approvalStatus = GoalApprovalStatus.APPROVED;
        this.rejectReason = null;
    }

    // 팀장 반려 - 사유 저장 (submittedAt 은 이력으로 유지)
    public void reject(String reason) {
        this.approvalStatus = GoalApprovalStatus.REJECTED;
        this.rejectReason = reason;
    }
}

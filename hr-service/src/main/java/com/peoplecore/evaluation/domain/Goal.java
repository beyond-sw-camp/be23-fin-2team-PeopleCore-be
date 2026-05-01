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

    // 가중치(%) — KPI 만 값을 가짐 (OKR 은 null)
    //   사원이 제출 화면에서 직접 입력. KPI 신규 등록 시 디폴트 10
    //   본인의 KPI 합 = 100 일 때만 제출 허용 (GoalService.submitAllDrafts 검증)
    @Column(name = "weight")
    private Integer weight;

    @Column(name = "target_value", precision = 12, scale = 2)
    private BigDecimal targetValue; // 목표값 (KPI만)

    @Column(name = "target_unit", length = 10)
    private String targetUnit; // 목표 단위

    // KPI 방향성 스냅샷 - 등록/수정 시점 template.direction 복사 (OKR이면 NULL)
    // 템플릿의 direction 이 후에 변경돼도 기존 목표의 달성률 계산은 등록 당시 기준 유지
    @Enumerated(EnumType.STRING)
    @Column(name = "kpi_direction", length = 10)
    private KpiDirection kpiDirection;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", length = 20)
    @Builder.Default
    private GoalApprovalStatus approvalStatus = GoalApprovalStatus.DRAFT; // 상태 (작성중/대기/승인/반려)

    @Column(name = "reject_reason", length = 500)
    private String rejectReason; // 반려 사유

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt; // 제출 시각

    // KPI 목표 수정 - 템플릿에서 필드 자동 복사
    //   weight 는 등록 시 디폴트 10 으로 박히고, 이후 changeWeight 로만 변경 (등록/수정 시 건드리지 않음)
    public void updateAsKpi(KpiTemplate template, BigDecimal targetValue) {
        this.kpiTemplate = template;
        this.goalType = GoalType.KPI;
        this.category = template.getCategory().getOptionValue();
        this.title = template.getName();
        this.description = template.getDescription();
        this.targetUnit = template.getUnit().getOptionValue();
        this.targetValue = targetValue;
        this.kpiDirection = template.getDirection();
        if (this.weight == null) this.weight = 10;   // KPI 신규 등록 시 디폴트
    }

    // OKR 목표 수정 - 사원 입력값 그대로, KPI 관련 필드는 NULL 처리
    //   OKR 은 가중치 개념 없음 - weight 는 항상 null
    public void updateAsOkr(String category, String title, String description) {
        this.kpiTemplate = null;
        this.goalType = GoalType.OKR;
        this.category = category;
        this.title = title;
        this.description = description;
        this.targetValue = null;
        this.targetUnit = null;
        this.kpiDirection = null;
        this.weight = null;
    }

    // 가중치 일괄 저장 — 제출 화면에서 호출
    public void changeWeight(int weight) {
        if (this.goalType != GoalType.KPI) {
            throw new IllegalStateException("KPI 목표만 가중치를 가질 수 있습니다");
        }
        this.weight = weight;
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

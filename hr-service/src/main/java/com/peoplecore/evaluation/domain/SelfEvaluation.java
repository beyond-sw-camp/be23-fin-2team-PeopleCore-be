package com.peoplecore.evaluation.domain;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 자기평가 - 목표별 자기평가 (1:1)
@Entity
@Table(name = "self_evaluation")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelfEvaluation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "self_eval_id")
    private Long selfEvalId; // 자기평가 PK

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id", nullable = false)
    private Goal goal; // 대상 목표

    @Column(name = "actual_value", precision = 12, scale = 2)
    private BigDecimal actualValue; // 실적값

    @Enumerated(EnumType.STRING)
    @Column(name = "achievement_level", length = 15)
    private AchievementLevel achievementLevel; // OKR 달성수준 (우수/양호/보통/부족/미흡)

    @Column(name = "achievement_detail", length = 1000)
    private String achievementDetail; // 달성 상세내용

    @Column(name = "evidence", length = 500)
    private String evidence; // 근거자료

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", length = 20)
    @Builder.Default
    private SelfEvalApprovalStatus approvalStatus = SelfEvalApprovalStatus.DRAFT; // 상태 (작성중/대기/승인/반려)

    @Column(name = "reject_reason", length = 500)
    private String rejectReason; // 반려 사유

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt; // 제출 시각

    // 임시저장 - 사원 입력값 upsert (상태/submittedAt 은 건드리지 않음)
    public void updateDraft(BigDecimal actualValue, AchievementLevel achievementLevel,
                            String achievementDetail, String evidence) {
        this.actualValue = actualValue;
        this.achievementLevel = achievementLevel;
        this.achievementDetail = achievementDetail;
        this.evidence = evidence;
    }

    // 반려된 자기평가 재수정 시 작성중 상태로 리셋 (재제출 가능)
    public void resetToDraft() {
        this.approvalStatus = SelfEvalApprovalStatus.DRAFT;
        this.submittedAt = null;
        this.rejectReason = null;
    }

    // 자기평가 제출 - 작성중/반려 상태에서 대기로 전환
    public void submit() {
        this.submittedAt = LocalDateTime.now();
        this.approvalStatus = SelfEvalApprovalStatus.PENDING;
        this.rejectReason = null;
    }

    // 팀장 승인 - 대기 상태만 승인 가능, 반려 사유 초기화
    public void approve() {
        this.approvalStatus = SelfEvalApprovalStatus.APPROVED;
        this.rejectReason = null;
    }

    // 팀장 반려 - 사유 저장 (submittedAt 은 이력으로 유지)
    public void reject(String reason) {
        this.approvalStatus = SelfEvalApprovalStatus.REJECTED;
        this.rejectReason = reason;
    }
}

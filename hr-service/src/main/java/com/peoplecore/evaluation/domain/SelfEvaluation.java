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

    @Column(name = "achievement_level", length = 10)
    private String achievementLevel; // OKR 달성수준 (우수/양호/보통/부족/미흡)

    @Column(name = "achievement_detail", length = 1000)
    private String achievementDetail; // 달성 상세내용

    @Column(name = "evidence", length = 500)
    private String evidence; // 근거자료

    @Column(name = "approval_status", length = 20)
    @Builder.Default
    private String approvalStatus = "대기"; // 승인 상태

    @Column(name = "reject_reason", length = 500)
    private String rejectReason; // 반려 사유

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt; // 제출 시각
}

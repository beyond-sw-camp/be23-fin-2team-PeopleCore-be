package com.peoplecore.approval.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.*;

/**
 * 결재라인
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalLine extends BaseTimeEntity {

    /**
     * 결재 라인 Id
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long lineId;

    /**
     * 회사 id
     */
    @Column(nullable = false)
    private UUID companyId;

    /**
     * 결재 문서 Id
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doc_id", nullable = false)
    private ApprovalDocument docId;

    /**
     * 사원번호 Id
     */
    @Column(nullable = false)
    private Long empId;

    /**
     * 사원 이름
     */
    @Column(nullable = false)
    private String empName;

    /**
     * 사원 부서 ID
     */
    private Long empDeptId;

    /**
     * 사원 부서 이름
     */
    @Column(nullable = false)
    private String empDeptName;

    /**
     * 결재 역할 - 결제자/참조자/열람자
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalRole approvalRole;

    /**
     * 처리 순서 - doc_id+step
     */
    @Column(nullable = false)
    private Integer lineStep;

    /**
     * 결재 상태
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalLineStatus approvalLineStatus = ApprovalLineStatus.PENDING;

    /* 처리 일시 */
    private LocalDateTime lineProcessedAt;

    /**
     * 반려 사유
     */
    private String lineRejectReason;

    /**
     * 위임 처리 여부 - default == false
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isDelegated = false;

    /**
     * 위임자 Id
     */
    private Long lineDelegatedId;

    /**
     * 열람 여부 - default == false
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    /**
     * 사원 직급
     */
    @Column(nullable = false)
    private String empGrade;

    /**
     * 사원 직책
     */
    @Column(nullable = false)
    private String empTitle;

    /**
     * 결재선 상태 초기화 (재기안 시 사용)
     */
    public void resetStatus() {
        this.approvalLineStatus = ApprovalLineStatus.PENDING;
        this.lineProcessedAt = null;
        this.lineRejectReason = null;
    }

    /*결재 승인 처리*/
    public void approve() {
        this.approvalLineStatus = ApprovalLineStatus.APPROVED;
        this.lineProcessedAt = LocalDateTime.now();
    }

    /*결재 반려 처리 */
    public void reject(String reason) {
        this.approvalLineStatus = ApprovalLineStatus.REJECTED;
        if (reason != null) {
            this.lineRejectReason = reason;
        }
        this.lineProcessedAt = LocalDateTime.now();
    }

    /* 회수/앞 결재자 반려로 인한 결재선 취소 처리 */
    public void cancel() {
        this.approvalLineStatus = ApprovalLineStatus.CANCELED;
        this.lineProcessedAt = LocalDateTime.now();
    }

    /*열람/ 참조 확인 처리 */
    public void markRead() {
        this.isRead = true;
        this.lineProcessedAt = LocalDateTime.now();
    }

}

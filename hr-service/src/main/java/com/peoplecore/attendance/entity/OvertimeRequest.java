package com.peoplecore.attendance.entity;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 초과근무 신청
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "overtime_request",
        indexes = {
                @Index(name = "idx_ot_req_company_status",
                        columnList = "company_id, ot_status"),
                @Index(name = "idx_ot_req_emp_date",
                        columnList = "emp_id, ot_date")
        }
)
public class OvertimeRequest extends BaseTimeEntity {

    /**
     * 초과근무신청 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long otId;

    /* 회사 Id*/
    @Column(nullable = false)
    private UUID companyId;

    /**
     * 사원 아이디
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;


    /**
     * 신청 기준 날짜 (해당 날짜의 초과근무)
     */
    @Column(nullable = false)
    private LocalDateTime otDate;

    /*계획 초과근무 시작 시각 */
    @Column(nullable = false)
    private LocalDateTime otPlanStart;

    /*계획 초과근무 종료 시각 */
    @Column(nullable = false)
    private LocalDateTime otPlanEnd;

    /* 초과근무 사유 */
    @Column(nullable = false)
    private String otReason;

    /*
     * 초과 근무 신청 상태 - > 전자결재 결과 캐시
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OtStatus otStatus;

    /*
     * 최종 승인자
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Employee manager;

    /*
     * collaboration-service 결재 문서 ID.
     *  - 상신 직후 RestClient 로 받은 값 저장
     *  - 상세 추적 / 결재 화면 딥링크 용도
     *  - 사전 신청 시점에 null 가능 (상신 API 반환 전)
     */
    @Column(name = "approval_doc_id")
    private Long approvalDocId;

    /*
     * 낙관적 락 - 승인/반려 동시 처리 방지
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /*
     * Kafka Consumer 에서 호출 — 결재 결과 캐시 업데이트.
     * 예외:
     *  - newStatus null 이면 IllegalArgumentException
     */
    public void applyApprovalResult(OtStatus newStatus, Employee manager) {
        if (newStatus == null) {
            throw new IllegalArgumentException("newStatus null 불가 - otId=" + this.otId);
        }
        this.otStatus = newStatus;
        this.manager = manager;
    }

    /* 상신 직후 반환된 문서 ID 저장 */
    public void bindApprovalDoc(Long docId) {
        this.approvalDocId = docId;
    }

}

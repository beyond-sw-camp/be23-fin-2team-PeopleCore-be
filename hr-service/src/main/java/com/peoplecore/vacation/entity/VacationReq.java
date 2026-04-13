package com.peoplecore.vacation.entity;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

/**
 * 휴가 신청
 * 스냅샷 필드는 요청 시점 보존용
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "vacation_req",
        indexes = {
                @Index(name = "idx_vac_req_company_status",
                        columnList = "company_id, vac_req_status"),
                @Index(name = "idx_vac_req_emp_period",
                        columnList = "emp_id, vac_req_startat, vac_req_endat")
        }
)
public class VacationReq extends BaseTimeEntity {

    /** 휴가 신청 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vac_req_id")
    private Long vacReqId;

    /** 회사 ID */
    @Column(nullable = false)
    private UUID companyId;

    /** 휴가 유형 ID */
    @Column(nullable = false)
    private Long infoId;

    /** 사원 아이디 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    /* 신청 시점 사원 이름 (스냅샷) */
    @Column(nullable = false)
    private String reqEmpName;

    /** 사원 부서명 */
    @Column(name = "req_emp_dept_name", nullable = false, length = 100)
    private String reqEmpDeptName;

    /** 휴가 시작 */
    @Column(nullable = false)
    private LocalDateTime vacReqStartat;

    /** 휴가 종료일 */
    @Column(nullable = false)
    private LocalDateTime vacReqEndat;

    /** 사용일수 */
    @Column(name = "vac_req_use_day", nullable = false, precision = 5, scale = 2)
    private BigDecimal vacReqUseDay;

    /** 휴가 사유 */
    private String vacReqReason;

    /** 승인 상태 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VacationStatus vacReqStatus;

    /** 처리 사원 ID */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Employee manager;

    /** 승인/반려 처리 시간 */
    private LocalDateTime vacReqUpdateAt;

    /** 반려 사유 */
    private String vacReqRejectReason;

    /** 사원 직급 */
    @Column(name = "req_emp_grade", nullable = false, length = 50)
    private String reqEmpGrade;

    /** 사원 직책 */
    @Column(name = "req_emp_title", nullable = false, length = 50)
    private String reqEmpTitle;

    /*
     * collaboration-service 결재 문서 ID.
     */
    @Column(name = "approval_doc_id")
    private Long approvalDocId;

    /** 낙관적 락 - 승인/반려 동시 처리 방지 */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /*
     * Kafka Consumer 에서 호출 — 결재 결과 캐시 업데이트.
     */
    public void applyApprovalResult(VacationStatus newStatus, Employee manager, String rejectReason) {
        if (newStatus == null) {
            throw new IllegalArgumentException("newStatus null 불가 - vacReqId=" + this.vacReqId);
        }
        this.vacReqStatus = newStatus;
        this.manager = manager;
        this.vacReqUpdateAt = LocalDateTime.now();
        if (newStatus == VacationStatus.REJECTED) {
            this.vacReqRejectReason = rejectReason;
        }
    }

    /*
     * 상신 직후 반환된 문서 ID 저장.
     */
    public void bindApprovalDoc(Long docId) {
        this.approvalDocId = docId;
    }
}

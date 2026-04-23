package com.peoplecore.vacation.entity;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/* 결재 연동 휴가 요청 공통 상위 - USE(VacationRequest) / GRANT(VacationGrantRequest) 공통 필드/행위 보유 */
/* @MappedSuperclass: 자체 테이블 X, 자식 테이블에 공통 컬럼 물리 복사 */
@MappedSuperclass
@Getter
@NoArgsConstructor
public abstract class AbstractApprovalBoundRequest extends BaseTimeEntity {

    /* 요청 ID (PK) - 자식 테이블별 독립 AUTO_INCREMENT */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    /* 회사 ID */
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    /* 휴가 유형 - LAZY. JOIN FETCH 권장 (화면 N+1 방지) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id", nullable = false)
    private VacationType vacationType;

    /* 요청 사원 - LAZY */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    /* 신청 시점 사원 이름 (스냅샷) - 조직개편 후에도 신청 당시 정보 보존 */
    @Column(name = "request_emp_name", nullable = false, length = 50)
    private String requestEmpName;

    /* 신청 시점 부서명 (스냅샷) */
    @Column(name = "request_emp_dept_name", nullable = false, length = 100)
    private String requestEmpDeptName;

    /* 신청 시점 직급 (스냅샷) */
    @Column(name = "request_emp_grade", nullable = false, length = 50)
    private String requestEmpGrade;

    /* 신청 시점 직책 (스냅샷) */
    @Column(name = "request_emp_title", nullable = false, length = 50)
    private String requestEmpTitle;

    /* 사용/부여 일수 - USE: 실제 소진 일수 / GRANT: 부여 요청 일수 */
    /* 1.0=종일 / 0.5=반차 / 0.125=1시간 / N.0=다일 */
    @Column(name = "request_use_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal requestUseDays;

    /* 요청 사유 */
    @Column(name = "request_reason")
    private String requestReason;

    /* 상태 - PENDING/APPROVED/REJECTED/CANCELED */
    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false, length = 20)
    private RequestStatus requestStatus;

    /* 처리자 - 결재 최종 승인/반려자 또는 관리자 직권 처리자. LAZY */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Employee manager;

    /* 승인/반려/취소 처리 시각 */
    @Column(name = "request_processed_at")
    private LocalDateTime requestProcessedAt;

    /* 반려 사유 - REJECTED 일 때만 */
    @Column(name = "request_reject_reason")
    private String requestRejectReason;

    /* collaboration-service ApprovalDocument PK - 첨부파일은 collab.commonAtachFile 로 연결 */
    @Column(name = "approval_doc_id")
    private Long approvalDocId;

    /* 낙관적 락 - 동시 승인/반려/취소 충돌 방지 */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;


    /* 공통 필드 protected 생성자 - 자식 엔티티의 팩토리 메서드에서 super(...) 로 호출 */
    protected AbstractApprovalBoundRequest(UUID companyId, VacationType vacationType, Employee employee,
                                           EmployeeSnapshot snapshot,
                                           BigDecimal requestUseDays, String requestReason,
                                           Long approvalDocId) {
        this.companyId = companyId;
        this.vacationType = vacationType;
        this.employee = employee;
        this.requestEmpName = snapshot.empName();
        this.requestEmpDeptName = snapshot.deptName();
        this.requestEmpGrade = snapshot.grade();
        this.requestEmpTitle = snapshot.title();
        this.requestUseDays = requestUseDays;
        this.requestReason = requestReason;
        this.requestStatus = RequestStatus.PENDING;
        this.approvalDocId = approvalDocId;
    }

    /* 상태 전이 - 사원 셀프 처리 (정상 전이만 허용). 허용되지 않은 전이는 INVALID_REQUEST_STATUS_TRANSITION */
    public void apply(RequestStatus next, Employee processedBy, String rejectReason) {
        if (!this.requestStatus.canTransitionTo(next)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST_STATUS_TRANSITION);
        }
        applyInternal(next, processedBy, rejectReason);
    }

    /* 상태 전이 - 관리자 직권 (정상 전이 규칙 우회). 호출부에서 ROLE 검증 필수 */
    /* ledger 에 manager_id + reason 필수 기록 */
    public void applyByAdmin(RequestStatus next, Employee admin, String reason) {
        applyInternal(next, admin, reason);
    }

    /* 결재 문서 ID 바인딩 - collab 상신 직후 호출 */
    public void bindApprovalDoc(Long docId) {
        this.approvalDocId = docId;
    }

    /* 내부 상태 변경 공통 로직 */
    private void applyInternal(RequestStatus next, Employee processedBy, String rejectReason) {
        if (next == null) {
            throw new IllegalArgumentException("next status null 불가 - requestId=" + this.requestId);
        }
        this.requestStatus = next;
        this.manager = processedBy;
        this.requestProcessedAt = LocalDateTime.now();
        if (next == RequestStatus.REJECTED) {
            this.requestRejectReason = rejectReason;
        }
    }

    /* 사원 스냅샷 record - 자식 팩토리 메서드 파라미터 묶음 */
    /* compact constructor 에서 Objects.requireNonNull 로 4개 필드 null 차단 */
    /* NOT NULL 컬럼을 빈 문자열로 우회하지 않고, 누락 시 즉시 예외로 가시화 (DLQ/재시도) */
    public record EmployeeSnapshot(String empName, String deptName, String grade, String title) {
        public EmployeeSnapshot {
            Objects.requireNonNull(empName, "empName null 불가");
            Objects.requireNonNull(deptName, "deptName null 불가");
            Objects.requireNonNull(grade, "grade null 불가");
            Objects.requireNonNull(title, "title null 불가");
        }
    }
}

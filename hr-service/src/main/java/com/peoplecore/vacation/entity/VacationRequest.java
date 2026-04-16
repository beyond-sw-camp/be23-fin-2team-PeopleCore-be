package com.peoplecore.vacation.entity;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/* 휴가 신청 - 전자결재 연동. 신청 시점 사원 정보는 스냅샷 보존 */
@Entity
@Table(
        name = "vacation_request",
        indexes = {
                @Index(name = "idx_vacation_request_company_status",
                        columnList = "company_id, request_status"),
                @Index(name = "idx_vacation_request_emp_period",
                        columnList = "emp_id, request_start_at, request_end_at"),
                @Index(name = "idx_vacation_request_approval_doc",
                        columnList = "approval_doc_id")
        }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationRequest extends BaseTimeEntity {

    /* 신청 ID (PK) */
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

    /* 신청 사원 - LAZY */
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

    /* 휴가 시작 일시 - 종일/반차/시간 휴가 모두 표현 */
    @Column(name = "request_start_at", nullable = false)
    private LocalDateTime requestStartAt;

    /* 휴가 종료 일시 */
    @Column(name = "request_end_at", nullable = false)
    private LocalDateTime requestEndAt;

    /* 사용 일수 - 1.0=종일 / 0.5=반차 / 0.125=1시간 / N.0=다일 */
    @Column(name = "request_use_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal requestUseDays;

    /* 휴가 사유 */
    @Column(name = "request_reason")
    private String requestReason;

    /* 신청 상태 - PENDING/APPROVED/REJECTED/CANCELED */
    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false, length = 20)
    private RequestStatus requestStatus;

    /* 처리자 사원 ID (결재 최종 승인/반려자, 또는 관리자 직권 처리자) - LAZY */
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


    /* 신청 생성 - Kafka docCreated 수신 시 PENDING 으로 INSERT */
    public static VacationRequest createPending(UUID companyId, VacationType vacationType, Employee employee, EmployeeSnapshot snapshot, LocalDateTime startAt, LocalDateTime endAt, BigDecimal useDays, String reason, Long approvalDocId) {
        return VacationRequest.builder()
                .companyId(companyId)
                .vacationType(vacationType)
                .employee(employee)
                .requestEmpName(nullToEmpty(snapshot.empName()))
                .requestEmpDeptName(nullToEmpty(snapshot.deptName()))
                .requestEmpGrade(nullToEmpty(snapshot.grade()))
                .requestEmpTitle(nullToEmpty(snapshot.title()))
                .requestStartAt(startAt)
                .requestEndAt(endAt)
                .requestUseDays(useDays)
                .requestReason(reason)
                .requestStatus(RequestStatus.PENDING)
                .approvalDocId(approvalDocId)
                .build();
    }

    /* 상태 전이 - 사원 셀프 처리 (정상 전이만 허용) */
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

    /* NOT NULL 컬럼 안전 저장용 - null → 빈 문자열 */
    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }


    /* 사원 스냅샷 record - createPending 파라미터 묶음용 */
    public record EmployeeSnapshot(String empName, String deptName, String grade, String title) {
    }
}
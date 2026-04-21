package com.peoplecore.vacation.entity;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
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

    /* === 이벤트 기반 휴가 메타 (출산/배우자출산/유산사산/공가 전용, null 허용) === */

    /* 증빙 파일 URL (MinIO) - 법정 휴가 증빙 첨부. 기존 연차/월차는 null */
    @Column(name = "proof_file_url", length = 500)
    private String proofFileUrl;

    /* 임신 주수 - 유산·사산휴가 주수별 일수 산정 근거. 그 외 유형은 null */
    @Column(name = "pregnancy_weeks")
    private Integer pregnancyWeeks;

    /* 공가 하위 사유 - 공가 신청 시 필수. 통계/증빙 구분용. 그 외 유형은 null */
    @Enumerated(EnumType.STRING)
    @Column(name = "official_leave_reason", length = 30)
    private OfficialLeaveReason officialLeaveReason;

    /* 관련 출산일 - 배우자출산휴가 90일 이내 사용 검증 근거. 그 외 유형은 null */
    @Column(name = "related_birth_date")
    private LocalDate relatedBirthDate;

    /* 낙관적 락 - 동시 승인/반려/취소 충돌 방지 */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;


    /* 신청 생성 - Kafka docCreated 수신 시 PENDING 으로 INSERT */
    /* 스냅샷 4개 필드는 EmployeeSnapshot record 의 compact constructor 에서 null 검증 - 호출부가 책임지고 채워야 함 */
    /* 이벤트 기반 메타(proofFileUrl/pregnancyWeeks/officialLeaveReason/relatedBirthDate) 는 유형별 선택 세팅 */
    public static VacationRequest createPending(UUID companyId, VacationType vacationType, Employee employee,
                                                EmployeeSnapshot snapshot,
                                                LocalDateTime startAt, LocalDateTime endAt,
                                                BigDecimal useDays, String reason, Long approvalDocId,
                                                EventMeta eventMeta) {
        return VacationRequest.builder()
                .companyId(companyId)
                .vacationType(vacationType)
                .employee(employee)
                .requestEmpName(snapshot.empName())
                .requestEmpDeptName(snapshot.deptName())
                .requestEmpGrade(snapshot.grade())
                .requestEmpTitle(snapshot.title())
                .requestStartAt(startAt)
                .requestEndAt(endAt)
                .requestUseDays(useDays)
                .requestReason(reason)
                .requestStatus(RequestStatus.PENDING)
                .approvalDocId(approvalDocId)
                .proofFileUrl(eventMeta != null ? eventMeta.proofFileUrl() : null)
                .pregnancyWeeks(eventMeta != null ? eventMeta.pregnancyWeeks() : null)
                .officialLeaveReason(eventMeta != null ? eventMeta.officialLeaveReason() : null)
                .relatedBirthDate(eventMeta != null ? eventMeta.relatedBirthDate() : null)
                .build();
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


    /* 사원 스냅샷 record - createPending 파라미터 묶음. */
    /* compact constructor 에서 Objects.requireNonNull 로 4개 필드 null 차단 → 호출부(Consumer)가 책임지고 채움 */
    /* NOT NULL 컬럼을 빈 문자열로 우회하지 않고, 누락 시 즉시 예외로 가시화 (DLQ/재시도) */
    public record EmployeeSnapshot(String empName, String deptName, String grade, String title) {
        public EmployeeSnapshot {
            Objects.requireNonNull(empName, "empName null 불가");
            Objects.requireNonNull(deptName, "deptName null 불가");
            Objects.requireNonNull(grade, "grade null 불가");
            Objects.requireNonNull(title, "title null 불가");
        }
    }

    /* 이벤트 기반 휴가 메타 record - createPending 의 이벤트성 필드 묶음 */
    /* 전부 nullable - SCHEDULED 유형은 null record 전달, EVENT_BASED 는 유형별 필수 필드 세팅 */
    public record EventMeta(String proofFileUrl, Integer pregnancyWeeks,
                            OfficialLeaveReason officialLeaveReason, LocalDate relatedBirthDate) {}
}
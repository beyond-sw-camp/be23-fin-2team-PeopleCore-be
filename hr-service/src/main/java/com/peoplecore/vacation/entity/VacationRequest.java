package com.peoplecore.vacation.entity;

import com.peoplecore.employee.domain.Employee;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/* 휴가 사용 신청 - 전자결재 연동. 보유 잔여(Balance) 에서 소진하는 요청 */
/* 공통 필드/메서드는 AbstractApprovalBoundRequest 참고 */
/* 승인 시 markPending → consume / 취소 시 releasePending or restore */
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
public class VacationRequest extends AbstractApprovalBoundRequest {

    /* 휴가 시작 일시 - 종일/반차/시간 휴가 모두 표현 */
    @Column(name = "request_start_at", nullable = false)
    private LocalDateTime requestStartAt;

    /* 휴가 종료 일시 */
    @Column(name = "request_end_at", nullable = false)
    private LocalDateTime requestEndAt;


    private VacationRequest(UUID companyId, VacationType vacationType, Employee employee,
                            EmployeeSnapshot snapshot,
                            LocalDateTime startAt, LocalDateTime endAt,
                            BigDecimal useDays, String reason, Long approvalDocId) {
        super(companyId, vacationType, employee, snapshot, useDays, reason, approvalDocId);
        this.requestStartAt = startAt;
        this.requestEndAt = endAt;
    }

    /* 신청 생성 - Kafka docCreated 수신 시 PENDING 으로 INSERT */
    public static VacationRequest createPending(UUID companyId, VacationType vacationType, Employee employee,
                                                EmployeeSnapshot snapshot,
                                                LocalDateTime startAt, LocalDateTime endAt,
                                                BigDecimal useDays, String reason, Long approvalDocId) {
        return new VacationRequest(companyId, vacationType, employee, snapshot,
                startAt, endAt, useDays, reason, approvalDocId);
    }
}

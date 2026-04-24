package com.peoplecore.vacation.entity;

import com.peoplecore.employee.domain.Employee;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/* 법정 근로 휴가 부여 신청 - 사원이 "이 휴가를 달라" 고 증빙 첨부해 요청 */
/* 공통 필드/메서드는 AbstractApprovalBoundRequest 참고 */
/* 승인 시 VacationBalance.accrue (pending/used 무변경). 취소 시 rollbackAccrual */
/* 증빙/출산일/공가사유는 결재 첨부파일 + requestReason 에서 담당자 육안 검토. 주수만 자동 산정용 컬럼 */
@Entity
@Table(
        name = "vacation_grant_request",
        indexes = {
                @Index(name = "idx_vgr_company_status",
                        columnList = "company_id, request_status"),
                @Index(name = "idx_vgr_emp",
                        columnList = "emp_id"),
                @Index(name = "idx_vgr_approval_doc",
                        columnList = "approval_doc_id")
        }
)
@Getter
@NoArgsConstructor
public class VacationGrantRequest extends AbstractApprovalBoundRequest {

    /* 임신 주수 - MISCARRIAGE 부여 신청 시 필수. 주수→일수 자동 산정(MiscarriageLeaveRule.daysForWeeks). 그 외 유형은 null */
    @Column(name = "pregnancy_weeks")
    private Integer pregnancyWeeks;

    /* 적립 시 사용한 balanceYear - APPROVED 시점에 markApplied() 로 기록 */
    /* 이후 취소 시 같은 year 의 Balance 를 정확히 찾아 rollbackAccrual */
    /* PENDING/REJECTED 는 null. 신청 ↔ 승인 사이 해 넘김 케이스에서도 정확한 추적 가능 */
    @Column(name = "applied_balance_year")
    private Integer appliedBalanceYear;


    private VacationGrantRequest(UUID companyId, VacationType vacationType, Employee employee,
                                 EmployeeSnapshot snapshot,
                                 BigDecimal useDays, String reason, Long approvalDocId,
                                 Integer pregnancyWeeks) {
        super(companyId, vacationType, employee, snapshot, useDays, reason, approvalDocId);
        this.pregnancyWeeks = pregnancyWeeks;
    }

    /* 부여 신청 생성 - Kafka grantDocCreated 수신 시 PENDING 으로 INSERT */
    /* pregnancyWeeks: MISCARRIAGE 만 필수 (서비스 검증), 그 외 유형은 null */
    public static VacationGrantRequest createPending(UUID companyId, VacationType vacationType, Employee employee,
                                                     EmployeeSnapshot snapshot,
                                                     BigDecimal useDays, String reason, Long approvalDocId,
                                                     Integer pregnancyWeeks) {
        return new VacationGrantRequest(companyId, vacationType, employee, snapshot,
                useDays, reason, approvalDocId, pregnancyWeeks);
    }

    /* 적립 시 사용한 balanceYear 기록 - applyApprovedAccrual 에서 accrue 성공 직후 호출 */
    public void markApplied(int balanceYear) {
        this.appliedBalanceYear = balanceYear;
    }
}

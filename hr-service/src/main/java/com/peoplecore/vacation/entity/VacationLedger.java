package com.peoplecore.vacation.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/* 잔여 변동 이력 - append-only. 모든 잔여 변동 추적용 (감사/정합성 검증/디버깅) */
@Entity
@Table(
        name = "vacation_ledger",
        indexes = {
                @Index(name = "idx_vacation_ledger_balance_created",
                        columnList = "company_id, balance_id, created_at"),
                @Index(name = "idx_vacation_ledger_ref",
                        columnList = "company_id, ref_type, ref_id")
        }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationLedger extends BaseTimeEntity {

    /* ref_type 상수 - String 으로 처리 (enum 안 만듦) */
    public static final String REF_VAC_REQUEST = "VAC_REQUEST";
    public static final String REF_SCHEDULER   = "SCHEDULER";
    public static final String REF_ADMIN       = "ADMIN";

    /* 이력 ID (PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ledger_id")
    private Long ledgerId;

    /* 회사 ID */
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    /* 대상 잔여 - LAZY */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "balance_id", nullable = false)
    private VacationBalance vacationBalance;

    /* 사원 ID - 조회 편의용 (balance.employee.empId 와 동일) */
    @Column(name = "emp_id", nullable = false)
    private Long empId;

    /* 변동 이벤트 타입 - ACCRUAL/USED/EXPIRED 등 */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private LedgerEventType eventType;

    /* 변동 일수 - +/- 부호. eventType.isCredit() 와 부호 일치해야 함 */
    @Column(name = "change_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal changeDays;

    /* 변동 전 totalDays */
    @Column(name = "before_total_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal beforeTotalDays;

    /* 변동 후 totalDays */
    @Column(name = "after_total_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal afterTotalDays;

    /* 참조 대상 구분 - VAC_REQUEST / SCHEDULER / ADMIN */
    @Column(name = "ref_type", length = 20)
    private String refType;

    /* 참조 대상 ID - VacationRequest.requestId / null(스케줄러) */
    @Column(name = "ref_id")
    private Long refId;

    /* 처리자 사원 ID - 시스템 자동 처리(스케줄러) 시 NULL */
    @Column(name = "manager_id")
    private Long managerId;

    /* 변동 사유 - 자유 텍스트 */
    @Column(name = "reason")
    private String reason;


    /* 월차 자동 적립 - 스케줄러 호출 */
    public static VacationLedger ofAccrual(VacationBalance balance, BigDecimal days,
                                           BigDecimal beforeTotal, BigDecimal afterTotal) {
        return baseBuilder(balance, LedgerEventType.ACCRUAL, days, beforeTotal, afterTotal)
                .refType(REF_SCHEDULER)
                .reason("월차 자동 적립")
                .build();
    }

    /* 연차 회기 발생 - 스케줄러 호출 */
    public static VacationLedger ofInitialGrant(VacationBalance balance, BigDecimal days,
                                                BigDecimal beforeTotal, BigDecimal afterTotal) {
        return baseBuilder(balance, LedgerEventType.INITIAL_GRANT, days, beforeTotal, afterTotal)
                .refType(REF_SCHEDULER)
                .reason("연차 회기 발생")
                .build();
    }

    /* 관리자 수동 부여 - 포상/리프레시/여름휴가/결혼휴가 등 */
    public static VacationLedger ofManualGrant(VacationBalance balance, BigDecimal days,
                                               BigDecimal beforeTotal, BigDecimal afterTotal,
                                               Long managerId, String reason) {
        return baseBuilder(balance, LedgerEventType.MANUAL_GRANT, days, beforeTotal, afterTotal)
                .refType(REF_ADMIN)
                .managerId(managerId)
                .reason(reason)
                .build();
    }

    /* 결재 승인 차감 - Kafka APPROVED 수신 시 */
    public static VacationLedger ofUsed(VacationBalance balance, BigDecimal days,
                                        BigDecimal beforeTotal, BigDecimal afterTotal,
                                        Long requestId, Long managerId) {
        return baseBuilder(balance, LedgerEventType.USED, days.negate(), beforeTotal, afterTotal)
                .refType(REF_VAC_REQUEST)
                .refId(requestId)
                .managerId(managerId)
                .reason("결재 승인 차감")
                .build();
    }

    /* 승인 후 취소 복원 - APPROVED→CANCELED 시 */
    public static VacationLedger ofRestored(VacationBalance balance, BigDecimal days,
                                            BigDecimal beforeTotal, BigDecimal afterTotal,
                                            Long requestId, Long managerId, String reason) {
        return baseBuilder(balance, LedgerEventType.RESTORED, days, beforeTotal, afterTotal)
                .refType(REF_VAC_REQUEST)
                .refId(requestId)
                .managerId(managerId)
                .reason(reason)
                .build();
    }

    /* 만료 소멸 - 만료 잡 / 1년 도달 시 */
    public static VacationLedger ofExpired(VacationBalance balance, BigDecimal days,
                                           BigDecimal beforeTotal, BigDecimal afterTotal,
                                           String reason) {
        return baseBuilder(balance, LedgerEventType.EXPIRED, days.negate(), beforeTotal, afterTotal)
                .refType(REF_SCHEDULER)
                .reason(reason)
                .build();
    }

    /* 1년 도달 월차→연차 전환 표식 - 실제 적립은 ofInitialGrant 가 별도 기록 */
    public static VacationLedger ofAnnualTransition(VacationBalance balance, BigDecimal days,
                                                    BigDecimal beforeTotal, BigDecimal afterTotal) {
        return baseBuilder(balance, LedgerEventType.ANNUAL_TRANSITION, days.negate(), beforeTotal, afterTotal)
                .refType(REF_SCHEDULER)
                .reason("1년 도달 월차→연차 전환")
                .build();
    }

    /* 공통 빌더 베이스 - balance 에서 회사/사원 자동 추출 */
    private static VacationLedgerBuilder baseBuilder(VacationBalance balance, LedgerEventType eventType,
                                                     BigDecimal changeDays, BigDecimal beforeTotal, BigDecimal afterTotal) {
        return VacationLedger.builder()
                .companyId(balance.getCompanyId())
                .vacationBalance(balance)
                .empId(balance.getEmployee().getEmpId())
                .eventType(eventType)
                .changeDays(changeDays)
                .beforeTotalDays(beforeTotal)
                .afterTotalDays(afterTotal);
    }
}
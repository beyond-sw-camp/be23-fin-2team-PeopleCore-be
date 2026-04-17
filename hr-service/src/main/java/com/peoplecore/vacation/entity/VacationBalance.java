package com.peoplecore.vacation.entity;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.entity.VacationType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/* 사원별 휴가 유형별 잔여 - (회사, 사원, 유형, 연도) UNIQUE. 빠른 조회 + 동시성 제어 */
/* 잔여 = total_days - used_days - pending_days - expired_days */
/* 컬럼 의미 분리: used=실제 사용 / pending=결재 대기 / expired=만료 소멸. 수당 계산 시 expired 제외하고 판단 */
@Entity
@Table(
        name = "vacation_balance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_vacation_balance_company_emp_type_year",
                columnNames = {"company_id", "emp_id", "type_id", "balance_year"}
        ),
        indexes = {
                @Index(name = "idx_vacation_balance_company_emp",
                        columnList = "company_id, emp_id"),
                @Index(name = "idx_vacation_balance_company_expires",
                        columnList = "company_id, expires_at")
        }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationBalance extends BaseTimeEntity {

    /* 잔여 ID (PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "balance_id")
    private Long balanceId;

    /* 회사 ID */
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    /* 휴가 유형 - LAZY */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id", nullable = false)
    private VacationType vacationType;

    /* 사원 - LAZY */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    /* 회기 연도 - 적립/사용 집계 단위 (예: 2026) */
    @Column(name = "balance_year", nullable = false)
    private Integer balanceYear;

    /* 적립 누적 - ACCRUAL/INITIAL_GRANT/MANUAL_GRANT/RESTORED 합. 이 컬럼은 절대 감소하지 않음 */
    @Column(name = "total_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal totalDays;

    /* 결재 승인 사용 누적 - 결재 APPROVED 시점 차감. 취소 시 감소 가능 (restore) */
    @Column(name = "used_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal usedDays;

    /* 결재 대기 사용 누적 - 결재 PENDING 시점 예약. APPROVED 시 used 로 이동, REJECTED/취소 시 감소 */
    @Column(name = "pending_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal pendingDays;

    /* 만료 소멸 누적 - 만료 잡 / 1년 도달 전환 시 증가. used 와 구분해서 수당 계산 정확도 유지 */
    @Column(name = "expired_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal expiredDays;

    /* 최초 적립일 - 회기 시작 또는 첫 적립 시점 */
    @Column(name = "granted_at")
    private LocalDate grantedAt;

    /* 만료일 - 만료 잡 조회용. NULL 이면 무기한 */
    @Column(name = "expires_at")
    private LocalDate expiresAt;

    /* 낙관적 락 - 결재 동시 승인 / 잔여 동시 차감 충돌 방지 */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;


    /* 신규 잔여 생성 - 첫 적립 시점에 호출 (월차 첫 적립 / 연차 회기 발생 / 관리자 부여) */
    /* expiredDays 초기 0 - 신규 balance 는 소멸 이력 없음 */
    public static VacationBalance createNew(UUID companyId, VacationType vacationType, Employee employee,
                                            Integer balanceYear, LocalDate grantedAt, LocalDate expiresAt) {
        return VacationBalance.builder()
                .companyId(companyId)
                .vacationType(vacationType)
                .employee(employee)
                .balanceYear(balanceYear)
                .totalDays(BigDecimal.ZERO)
                .usedDays(BigDecimal.ZERO)
                .pendingDays(BigDecimal.ZERO)
                .expiredDays(BigDecimal.ZERO)
                .grantedAt(grantedAt)
                .expiresAt(expiresAt)
                .build();
    }


    /* 잔여 적립 - ACCRUAL/INITIAL_GRANT/MANUAL_GRANT 시 호출. total += days */
    /* 캡 검증(월차 11일 등)은 호출부 책임 (MonthlyAccrualScheduler 가 코드 상수로 처리) */
    public void accrue(BigDecimal days) {
        validatePositive(days);
        this.totalDays = this.totalDays.add(days);
    }

    /* 신청 예약 - PENDING 시 호출. pending += days, 잔여 부족 시 예외 */
    public void markPending(BigDecimal days) {
        validatePositive(days);
        if (getAvailableDays().compareTo(days) < 0) {
            throw new CustomException(ErrorCode.VACATION_BALANCE_INSUFFICIENT);
        }
        this.pendingDays = this.pendingDays.add(days);
    }

    /* 승인 차감 - APPROVED 시 호출. pending -= days, used += days */
    public void consume(BigDecimal days) {
        validatePositive(days);
        if (this.pendingDays.compareTo(days) < 0) {
            throw new CustomException(ErrorCode.VACATION_BALANCE_PENDING_INSUFFICIENT);
        }
        this.pendingDays = this.pendingDays.subtract(days);
        this.usedDays = this.usedDays.add(days);
    }

    /* 신청 예약 해제 - REJECTED / PENDING→CANCELED 시 호출. pending -= days */
    public void releasePending(BigDecimal days) {
        validatePositive(days);
        if (this.pendingDays.compareTo(days) < 0) {
            throw new CustomException(ErrorCode.VACATION_BALANCE_PENDING_INSUFFICIENT);
        }
        this.pendingDays = this.pendingDays.subtract(days);
    }

    /* 사용 복원 - APPROVED→CANCELED 시 호출. used -= days */
    public void restore(BigDecimal days) {
        validatePositive(days);
        if (this.usedDays.compareTo(days) < 0) {
            throw new CustomException(ErrorCode.VACATION_BALANCE_USED_INSUFFICIENT);
        }
        this.usedDays = this.usedDays.subtract(days);
    }

    /* 잔여 만료 - 만료 잡 / 1년 도달 시. 남은 잔여를 expired 로 이동 → 잔여 0 */
    /* used 와 분리됨 - LeaveAllowance 의 (total - used) 미사용량 계산이 만료 후에도 정확 */
    public BigDecimal expireRemaining() {
        BigDecimal remaining = getAvailableDays();
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        this.expiredDays = this.expiredDays.add(remaining);
        return remaining;
    }

    /* 사용 가능 잔여 = total - used - pending - expired */
    public BigDecimal getAvailableDays() {
        return this.totalDays
                .subtract(this.usedDays)
                .subtract(this.pendingDays)
                .subtract(this.expiredDays);
    }

    /* 양수 검증 */
    private void validatePositive(BigDecimal days) {
        if (days == null || days.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("days 는 양수여야 함 - balanceId=" + balanceId + ", days=" + days);
        }
    }
}
package com.peoplecore.entity;

import com.peoplecore.enums.CompanyStatus;
import com.peoplecore.enums.ContractType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "company")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Company {

    @Id
    @UuidGenerator
    @Column(name = "company_id", updatable = false, nullable = false)
    private UUID companyId;

    @Column(name = "company_name", nullable = false, length = 100)
    private String companyName;

    @Column(name = "founding_date")
    private LocalDate foundingDate;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    // ── 계약 정보 ──
    @Column(name = "contract_start_date", nullable = false)
    private LocalDate contractStartDate;

    @Column(name = "contract_end_date", nullable = false)
    private LocalDate contractEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, length = 10)
    private ContractType contractType;

    @Column(name = "max_employees", nullable = false)
    private Integer maxEmployees;

    // ── 상태 관리 ──
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CompanyStatus status = CompanyStatus.PENDING;

    // ── 만료 알림 추적 ──
    @Column(name = "last_notified_days")
    private Integer lastNotifiedDays; // 마지막으로 알림 보낸 D-day (30, 14, 7, 1)

    // ── 담당자 정보 ──
    @Column(name = "contact_name", length = 50)
    private String contactName;

    @Column(name = "contact_email", length = 100)
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    // ── 감사 필드 ──
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── 비즈니스 메서드 ──

    /**
     * 회사 상태 변경
     * - PENDING → ACTIVE (계약 확정 시)
     * - ACTIVE → SUSPENDED (관리자 수동 중지)
     * - ACTIVE → EXPIRED (만료 처리)
     * - SUSPENDED → ACTIVE (재활성화)
     */
    public void changeStatus(CompanyStatus newStatus) {
        this.status = newStatus;
        // 상태 변경 시 알림 추적 초기화
        if (newStatus == CompanyStatus.ACTIVE) {
            this.lastNotifiedDays = null;
        }
    }

    /**
     * 계약 연장 처리
     * - 만료일 재설정
     * - max_employees, 계약유형 변경 가능
     * - EXPIRED → ACTIVE 자동 복구
     * - 알림 초기화
     */
    public void extendContract(LocalDate newEndDate, Integer newMaxEmployees,
                                ContractType newContractType) {
        this.contractEndDate = newEndDate;
        if (newMaxEmployees != null) {
            this.maxEmployees = newMaxEmployees;
        }
        if (newContractType != null) {
            this.contractType = newContractType;
        }
        // EXPIRED 상태면 ACTIVE로 자동 복구
        if (this.status == CompanyStatus.EXPIRED) {
            this.status = CompanyStatus.ACTIVE;
        }
        // 알림 초기화
        this.lastNotifiedDays = null;
    }

    /**
     * 만료 알림 발송 기록
     */
    public void markNotified(int dDay) {
        this.lastNotifiedDays = dDay;
    }
}

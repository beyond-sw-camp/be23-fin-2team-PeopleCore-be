package com.peoplecore.pay.domain;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.pay.enums.PayrollEmpStatusType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "payroll_emp_status", //사원별 급여산정 상태
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payroll_emp",
                columnNames = {"payroll_run_id", "emp_id"}
        ),
        indexes = {
                @Index(name = "idx_pes_run", columnList = "payroll_run_id"),
                @Index(name = "idx_pes_run_status", columnList = "payroll_run_id, status")
        })
public class PayrollEmpStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_run_id", nullable = false)
    private PayrollRuns payrollRuns;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PayrollEmpStatusType status = PayrollEmpStatusType.CALCULATING;

    private LocalDateTime confirmedAt;
    private Long confirmedBy;

    @Column(nullable = false)
    private UUID companyId;

    private Long approvalDocId;     // 어떤 결재 문서에 포함됐는지
    private LocalDateTime paidAt;   // 지급 완료 시각


//  확정
    public void confirm(Long byEmpId) {
        this.status = PayrollEmpStatusType.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
        this.confirmedBy = byEmpId;
    }
//  확정취소
    public void revert() {
        if (this.approvalDocId != null) {
            throw new IllegalStateException("결재 진행 중인 사원은 확정취소 불가");
        }
        this.status = PayrollEmpStatusType.CALCULATING;
        this.confirmedAt = null;
        this.confirmedBy = null;
    }
//  결재 상신 후(승인 전)
    public void bindApprovalDoc(Long approvalDocId) {
        this.approvalDocId = approvalDocId;
    }
//  결재 승인 후
    public void approve() {
        if (this.status == PayrollEmpStatusType.APPROVED) return;  // 멱등
        if (this.status != PayrollEmpStatusType.CONFIRMED) {
            throw new IllegalStateException("확정 상태에서만 결재 승인 가능");
        }
        this.status = PayrollEmpStatusType.APPROVED;
    }
//  지급처리
    public void markPaid() {
        if (this.status == PayrollEmpStatusType.PAID) return;  // 멱등
        if (this.status != PayrollEmpStatusType.APPROVED) {
            throw new IllegalStateException("결재 승인된 사원만 지급 처리 가능");
        }
        this.status = PayrollEmpStatusType.PAID;
        this.paidAt = LocalDateTime.now();
    }
//  결재 반려/회수
    public void unbindApprovalDoc() {
        this.approvalDocId = null;
        if (this.status == PayrollEmpStatusType.APPROVED) {
            this.status = PayrollEmpStatusType.CONFIRMED;
        }
    }
}

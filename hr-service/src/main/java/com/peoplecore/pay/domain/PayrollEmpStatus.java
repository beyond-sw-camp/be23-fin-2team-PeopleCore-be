package com.peoplecore.pay.domain;

import com.peoplecore.employee.domain.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "payroll_emp_status",
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

    public void confirm(Long byEmpId) {
        this.status = PayrollEmpStatusType.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
        this.confirmedBy = byEmpId;
    }

    public void revert() {
        this.status = PayrollEmpStatusType.CALCULATING;
        this.confirmedAt = null;
        this.confirmedBy = null;
    }
}

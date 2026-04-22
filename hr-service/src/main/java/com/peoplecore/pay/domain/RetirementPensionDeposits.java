package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.pay.enums.DepStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "retirement_pension_deposits")   //퇴직연금적립-DC형
public class RetirementPensionDeposits {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long depId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

//    적립기준임금
    @Column(nullable = false)
    private Long baseAmount;

//    적립금액 : 연간임금/12
    @Column(nullable = false)
    private Long depositAmount;

    private LocalDateTime depositDate;

//    퇴직연금 상태 (적립예정,완료)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DepStatus depStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_run_id")
    private PayrollRuns payrollRun;

//    수동등록여부
    @Column(nullable = false)
    @Builder.Default
    private Boolean isManual = false;

//    적립기준 월(YYYY-MM)
    @Column(nullable = false, length = 7)
    private String payYearMonth;

//    수정등록시 / 취소시 사유
    @Column(length = 500)
    private String reason;

//    수동등록한 관리자 ID
    private Long createdBy;

//    취소일시
    private LocalDateTime canceledAt;

//    취소한 관리자 ID
    private Long canceledBy;



//    상태 전이 메서드
    public void cancel(Long adminEmpId, String cancelReason){
        if (this.depStatus == DepStatus.CANCELED) {
            throw new IllegalStateException("이미 취소된 적립입니다.");
        }
        this.depStatus = DepStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
        this.canceledBy = adminEmpId;
        this.reason = cancelReason;
    }

}

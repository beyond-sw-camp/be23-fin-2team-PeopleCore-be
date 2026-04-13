package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.pay.enums.PayrollStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "payroll_runs")   //급여산정
public class PayrollRuns {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long payrollRunId;

    @Column(nullable = false, length = 7)
    private String payYearMonth;
//    대상직원수
    private Integer totalEmployees;
    private Long totalPay;
    private Long totalDeduction;
    private Long totalNetPay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayrollStatus payrollStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    private LocalDate payDate;


    public void updateTotals(int empCount, long totalPay, long totalDeduction, long netPay){
        this.totalEmployees = empCount;
        this.totalPay = totalPay;
        this.totalDeduction = totalDeduction;
        this.totalNetPay = netPay;
    }
}

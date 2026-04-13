package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
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
@Table(name = "insurance_settlement",         //정산보험
        indexes = {
                @Index(name = "idx_settlement_company_month", columnList = "company_id, pay_year_month"),
                @Index(name = "idx_settlement_payroll_run", columnList = "payroll_run_id"),
                @Index(name = "idx_settlement_emp", columnList = "emp_id, pay_year_month")
        })
public class InsuranceSettlement extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long settlementId;

    @Column(nullable = false, length = 7)
    private String payYearMonth;    //적용 연월

    @Column(nullable = false)
    private Long baseSalary;

//    국민연금
    @Column(nullable = false)
    private Long pensionEmployee;
    @Column(nullable = false)
    private Long pensionEmployer;

//    건강보험
    @Column(nullable = false)
    private Long healthEmployee;
    @Column(nullable = false)
    private Long healthEmployer;

//    장기요양보험
    @Column(nullable = false)
    private Long ltcEmployee;
    @Column(nullable = false)
    private Long ltcEmployer;

//    고용보험
    @Column(nullable = false)
    private Long employmentEmployee;
    @Column(nullable = false)
    private Long employmentEmployer;

//    산재보험
    @Column(nullable = false)
    private Long industrialEmployer;

    @Column(nullable = false)
    private Long totalEmployee;
    @Column(nullable = false)
    private Long totalEmployer;
    @Column(nullable = false)
    private Long totalAmount;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isApplied = false;  //급여반영여부
    private LocalDateTime appliedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_run_id", nullable = false)
    private PayrollRuns payrollRuns;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insurance_rates", nullable = false)
    private InsuranceRates insuranceRates;

    // ── 정산기간 ──
    @Column(nullable = false, length = 7)
    private String settlementFromMonth;    // 정산 시작월 (예: "2026-01")
    @Column(nullable = false, length = 7)
    private String settlementToMonth;      // 정산 종료월 (예: "2026-12")

    // ── 기공제액 (정산기간 내 급여대장에서 이미 공제한 누적액) ──
    @Column(nullable = false)
    private Long deductedPension;           // 기공제 국민연금
    @Column(nullable = false)
    private Long deductedHealth;            // 기공제 건강보험
    @Column(nullable = false)
    private Long deductedLtc;               // 기공제 장기요양
    @Column(nullable = false)
    private Long deductedEmployment;        // 기공제 고용보험
    @Column(nullable = false)
    private Long totalDeducted;             // 기공제 합계

    // ── 차액 (정산액 - 기공제액) : 양수=추가징수, 음수=환급 ──
    @Column(nullable = false)
    private Long diffPension;
    @Column(nullable = false)
    private Long diffHealth;
    @Column(nullable = false)
    private Long diffLtc;
    @Column(nullable = false)
    private Long diffEmployment;
    @Column(nullable = false)
    private Long totalDiff;                 // 차액 합계



//    급여반영처리
    public void markApplied(){
        this.isApplied = true;
        this.appliedAt = LocalDateTime.now();
    }

//    재산정시 값 갱신
    public void recalculate(Long baseSalary, Long pensionEmp, Long pensionEmpr, Long healthEmp, Long healthEmpr, Long ltcEmp, Long ltcEmpr, Long employmentEmp, Long employmentEmpr, Long industrialEmpr, Long dedPension, Long dedHealth, Long dedLtc, Long dedEmployment){
        this.baseSalary = baseSalary;
        this.pensionEmployee = pensionEmp;
        this.pensionEmployer = pensionEmpr;
        this.healthEmployee = healthEmp;
        this.healthEmployer = healthEmpr;
        this.ltcEmployee = ltcEmp;
        this.ltcEmployer = ltcEmpr;
        this.employmentEmployee = employmentEmp;
        this.employmentEmployer = employmentEmpr;
        this.industrialEmployer = industrialEmpr;
        this.totalEmployee = pensionEmp + healthEmp + ltcEmp + employmentEmp;
        this.totalEmployer = pensionEmpr + healthEmpr + ltcEmpr + employmentEmpr;
        this.totalAmount = this.totalEmployee + this.totalEmployer;

        // 기공제액
        this.deductedPension = dedPension;
        this.deductedHealth = dedHealth;
        this.deductedLtc = dedLtc;
        this.deductedEmployment = dedEmployment;
        this.totalDeducted = dedPension + dedHealth + dedLtc + dedEmployment;

        // 차액
        this.diffPension = pensionEmp - dedPension;
        this.diffHealth = healthEmp - dedHealth;
        this.diffLtc = ltcEmp - dedLtc;
        this.diffEmployment = employmentEmp - dedEmployment;
        this.totalDiff = this.totalEmployee - this.totalDeducted;
    }

}

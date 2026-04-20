package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
import com.peoplecore.pay.enums.AllowanceStatus;
import com.peoplecore.pay.enums.AllowanceType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "leave_allowance",    //연차수당
    indexes = {
        @Index(name = "idx_leave_allowance_company_year", columnList = "company_id, year, allowance_type"),
            @Index(name = "idx_leave_allowance_emp", columnList = "emp_id, year")
    })
public class LeaveAllowance extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long allowanceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private Integer year;           //기준연도

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AllowanceType allowanceType;

    private Long normalMonthlySalary;   //통상임금(월)
    private Long dailyWage;             //일 통상임금

    @Column(precision = 5, scale = 1)
    private BigDecimal totalLeaveDays;  //연차 부여일수

    @Column(precision = 5, scale = 1)
    private BigDecimal usedLeaveDays;   //연차 사용일수

    @Column(precision = 5, scale = 1)
    private BigDecimal unusedLeaveDays; //연차 미사용일수

    private Long allowanceAmount;       //산정금액

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AllowanceStatus status = AllowanceStatus.PENDING;

    private Long appliedPayrollRunId;       // 반영된 급여대장 ID
    private String appliedMonth;            // 반영월 (yyyy-MM)

    private LocalDate resignDate;           // 퇴직일 (퇴직자용)


//    수당 산정
    public void calculate(Long normalMonthlySalary, Long dailyWage, BigDecimal totalDays, BigDecimal usedDays, BigDecimal unusedDays, Long amount) {
        this.normalMonthlySalary = normalMonthlySalary;
        this.dailyWage = dailyWage;
        this.totalLeaveDays = totalDays;
        this.usedLeaveDays = usedDays;
        this.unusedLeaveDays = unusedDays;
        this.status = AllowanceStatus.CALCULATED;
    }

//    급여대장 반영 완료
    public void markApplied(Long payrollRunId, String appliedMonth){
        this.appliedPayrollRunId = payrollRunId;
        this.appliedMonth = appliedMonth;
        this.status = AllowanceStatus.APPLIED;
    }

//    연차 소멸/수당발생 일자 계산
    public LocalDate resolveExpiredDate(String fiscalYearStart){
        return this.allowanceType.resolveExpiredDate(this, fiscalYearStart);
    }

//    퇴직금 평균임금 산정 대상 여부 판정
//    소멸일이 resignedDate -1년 ~ resignDate 범위 내에 있으면 true
//    산정완료(CALCULATED)/급여반영(APPLIED) 상태에서만
    public boolean isInSeverancePeriod(LocalDate resignDate, String fiscalYearStart){
        if (this.status != AllowanceStatus.CALCULATED && this.status != AllowanceStatus.APPLIED){
            return false;
        }
        LocalDate expired = resolveExpiredDate(fiscalYearStart);
        if (expired == null) return false;

        LocalDate periodStart = resignDate.minusYears(1);
        return !expired.isBefore(periodStart) && !expired.isAfter(resignDate);
    }
}

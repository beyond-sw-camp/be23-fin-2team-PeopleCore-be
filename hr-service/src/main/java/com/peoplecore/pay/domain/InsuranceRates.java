package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.entity.BaseTimeEntity;
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
@Table(name = "insurance_rates_id")     //사대보험요율
public class InsuranceRates extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long insuranceRatesId;

//    적용연도
    @Column(nullable = false)
    private Integer year;

//    국민연금요율
    @Column(precision = 5, scale = 4)
    private BigDecimal nationalPension;

//    건강보험요율
    @Column(precision = 5, scale = 4)
    private BigDecimal healthInsurance;

//    장기요양보험요율
    @Column(precision = 5, scale = 4)
    private BigDecimal longTermCare;

//    고용보험요율(근로자)
    @Column(precision = 5, scale = 4)
    private BigDecimal employmentInsurance;

//    고용보험요율(사업주)
    @Column(precision = 5, scale = 4)
    private BigDecimal employmentInsuranceEmployer;

//    산재보험요율
    @Column(precision = 5, scale = 4)
    private BigDecimal industrialAccident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insurance_job_types", nullable = false)
    private InsuranceJobTypes jobTypes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

//    보험요율 유효시작일
    @Column(nullable = false)
    private LocalDate validFrom;

//    보험요율 유효종료일
    private LocalDate validTo;

//    국민연금 상한/하한액
    @Column(nullable = false)
    private Long pensionUpperLimit;
    @Column(nullable = false)
    private Long pensionLowerLimit;

    // 고용보험 사업주 요율 수정
    public void updateEmployerRate(BigDecimal employmentInsuranceEmployer){
        this.employmentInsuranceEmployer = employmentInsuranceEmployer;
    }
}

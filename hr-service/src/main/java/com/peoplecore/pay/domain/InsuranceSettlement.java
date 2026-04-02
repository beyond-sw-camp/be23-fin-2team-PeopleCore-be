package com.peoplecore.pay.domain;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "insurance_settlement")   //정산보험
public class InsuranceSettlement extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long settlementId;

    @Column(nullable = false, length = 7)
    private String payYearMonth;
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
    @Column(nullable = false)
    private Boolean isApplied;
    private LocalDateTime appliedAt;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private Long empId;

    @Column(nullable = false)
    private Long payrollRunId;

    @Column(nullable = false)
    private Long insuranceRatesId;

}

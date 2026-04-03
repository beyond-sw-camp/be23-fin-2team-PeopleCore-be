package com.peoplecore.pay.domain;

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
@Table(name = "insurance_settlement")
public class InsuranceSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long settlementId;

    @Column(nullable = false, length = 7)
    private String payYearMonth;

    private Long baseSalary;

//    국민연금
    private Long pensionEmployee;
    private Long pensionEmployer;

//    건강보험
    private Long healthEmployee;
    private Long healthEmployer;

//    장기요양보험
    private Long ltcEmployee;
    private Long ltcEmployer;

//    고용보험
    private Long employmentEmployee;
    private Long employmentEmployer;

//    산재보험
    private Long industrialEmployer;

    private Long totalEmployee;
    private Long totalEmployer;
    private Long totalAmount;
    private LocalDateTime appliedAt;




}

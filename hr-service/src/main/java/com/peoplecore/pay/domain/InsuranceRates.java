package com.peoplecore.pay.domain;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

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

//    고용보험요율
    @Column(precision = 5, scale = 4)
    private BigDecimal employmentInsurance;

//    산재보험요율
    @Column(precision = 5, scale = 4)
    private BigDecimal industrialAccident;

    @Column(nullable = false)
    private Long jobTypesId;

    @Column(nullable = false)
    private UUID companyId;

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

}

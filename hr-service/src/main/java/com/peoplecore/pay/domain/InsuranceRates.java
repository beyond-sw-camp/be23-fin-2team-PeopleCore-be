package com.peoplecore.pay.domain;

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
@Table(name = "insurance_rates_id")
public class InsuranceRates {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long insuranceRatesId;

    @Column(nullable = false)
    private Integer year;

    @Column(precision = 5, scale = 4)
    private BigDecimal nationalPension;

    @Column(precision = 5, scale = 4)
    private BigDecimal healthInsurance;

    @Column(precision = 5, scale = 4)
    private BigDecimal longTermCare;

    @Column(precision = 5, scale = 4)
    private BigDecimal employmentInsurance;

    @CreationTimestamp
    private LocalDateTime createAt;

    @Column(precision = 5, scale = 4)
    private BigDecimal industrialAccident;

    @Column(nullable = false)
    private Long jobTypesId;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private LocalDate validFrom;

    private LocalDate validTo;
    private Long pensionUpperLimit;
    private Long pensionLowerLimit;

}

package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "insurance_job_types"     //업종(산재보험구분용)
//    indexes = {
//        @Index(name="idx_settlement_emp_month", columnList = "emp_id, pay_year_month"),
//        @Index(name = "idx_settlement_payroll_run", columnList = "payroll_run_id")
//        }
    )
public class InsuranceJobTypes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long jobTypesId;

    @Column(nullable = false, length = 50)
    private String jobTypeName;

//    산재보험요율
    @Column(precision = 5, scale = 4)
    private BigDecimal industrialAccidentRate;

    private String description;

    @Builder.Default
    private Boolean isActive = true;

    @Builder.Default
    private Boolean isDeleted = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;


    public void update(String name,String description, BigDecimal industrialAccidentRate){
        this.jobTypeName = name;
        this.description = description;
        this.industrialAccidentRate =industrialAccidentRate;
    }

    public void toggleActive(){
        this.isActive = !this.isActive;
    }

    public void softDelete(){
        this.isDeleted = true;
        this.isActive = false;
    }
}

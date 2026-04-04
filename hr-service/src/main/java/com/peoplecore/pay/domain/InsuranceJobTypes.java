package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "insurance_job_types")    //업종(산재보험구분용)
public class InsuranceJobTypes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long jobTypesId;

    @Column(nullable = false, length = 50)
    private String name;

    private String description;
    private Boolean isActive;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
}

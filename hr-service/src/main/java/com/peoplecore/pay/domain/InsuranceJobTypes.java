package com.peoplecore.pay.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "insurance_job_types")
public class InsuranceJobTypes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long jobTypesId;

    @Column(nullable = false, length = 50)
    private String name;

    private String description;
    private Boolean isActive;

    @Column(nullable = false)
    private UUID companyId;
}

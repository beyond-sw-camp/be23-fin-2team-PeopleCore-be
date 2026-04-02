package com.peoplecore.company.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "company")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    @Id
    @GeneratedValue
    private UUID companyId;

    @Column(nullable = false, unique = true)
    private String companyName;

    private LocalDate foundedAt;

    @Column(unique = true)
    private String companyIp;

    @Column(nullable = false)
    private LocalDate contractStartAt;

    @Column(nullable = false)
    private LocalDate contractEndAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractType contractType;

    @Column(nullable = false)
    private Integer maxEmployees;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CompanyStatus companyStatus = CompanyStatus.PENDING;

}

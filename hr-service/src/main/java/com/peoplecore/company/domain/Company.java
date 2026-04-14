package com.peoplecore.company.domain;

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

    @Column(nullable = false)
    private String companyName;

    private LocalDate foundedAt;

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

//    비밀번호 강제변경 여부
    @Builder.Default
    @Column(nullable = false)
    private Boolean forcePasswordChange = true;


    public void changeStatus(CompanyStatus newstatus){
        this.companyStatus = newstatus;
    }

//    계약연장
    public void extendContract(LocalDate newEndDate, Integer newMaxEmployees, ContractType newContractType){
        this.contractEndAt = newEndDate;
        if(newMaxEmployees != null) this.maxEmployees = newMaxEmployees;
        if(newContractType != null) this.contractType = newContractType;
        if (this.companyStatus == CompanyStatus.EXPIRED) this.companyStatus = CompanyStatus.ACTIVE;
    }
}

package com.peoplecore.company.dtos;

import com.peoplecore.company.domain.CompanyStatus;
import com.peoplecore.company.domain.ContractType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyResponse {

    private UUID companyId;
    private String companyName;
    private LocalDate contractStartAt;
    private LocalDate contractEndAt;
    private ContractType contractType;
    private Integer maxEmployees;
    private CompanyStatus companyStatus;
}

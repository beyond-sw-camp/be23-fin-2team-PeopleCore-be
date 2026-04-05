package com.peoplecore.company.dtos;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.domain.CompanyStatus;
import com.peoplecore.company.domain.ContractType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CompanyResDto {

    private UUID companyId;
    private String companyName;
    private LocalDate foundedAt;
    private String companyIp;
    private LocalDate contractStartAt;
    private LocalDate contractEndAt;
    private ContractType contractType;
    private Integer maxEmployees;
    private CompanyStatus companyStatus;


    public static CompanyResDto fromEntity(Company company){
        return CompanyResDto.builder()
                .companyId(company.getCompanyId())
                .companyName(company.getCompanyName())
                .foundedAt(company.getFoundedAt())
                .companyIp(company.getCompanyIp())
                .contractStartAt(company.getContractStartAt())
                .contractEndAt(company.getContractEndAt())
                .contractType(company.getContractType())
                .maxEmployees(company.getMaxEmployees())
                .companyStatus(company.getCompanyStatus())
                .build();
    }
}

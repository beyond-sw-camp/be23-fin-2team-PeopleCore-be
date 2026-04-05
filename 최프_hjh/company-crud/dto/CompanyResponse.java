package com.peoplecore.dto;

import com.peoplecore.entity.Company;
import com.peoplecore.enums.CompanyStatus;
import com.peoplecore.enums.ContractType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class CompanyResponse {

    private UUID companyId;
    private String companyName;
    private LocalDate foundingDate;
    private String ipAddress;

    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
    private ContractType contractType;
    private Integer maxEmployees;

    private CompanyStatus status;

    private String contactName;
    private String contactEmail;
    private String contactPhone;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CompanyResponse from(Company company) {
        return CompanyResponse.builder()
                .companyId(company.getCompanyId())
                .companyName(company.getCompanyName())
                .foundingDate(company.getFoundingDate())
                .ipAddress(company.getIpAddress())
                .contractStartDate(company.getContractStartDate())
                .contractEndDate(company.getContractEndDate())
                .contractType(company.getContractType())
                .maxEmployees(company.getMaxEmployees())
                .status(company.getStatus())
                .contactName(company.getContactName())
                .contactEmail(company.getContactEmail())
                .contactPhone(company.getContactPhone())
                .createdAt(company.getCreatedAt())
                .updatedAt(company.getUpdatedAt())
                .build();
    }
}

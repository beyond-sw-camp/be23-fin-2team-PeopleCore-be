package com.peoplecore.company.dtos;

import com.peoplecore.company.entity.Company;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternalCompanyResponseDto {
    private UUID companyId;
    private String companyName;

    public static InternalCompanyResponseDto from(Company company) {
        return InternalCompanyResponseDto.builder()
                .companyId(company.getCompanyId())
                .companyName(company.getCompanyName())
                .build();
    }
}

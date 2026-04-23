package com.peoplecore.company.dtos;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.domain.copilot.CopilotConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CopilotContextResDto {

    private UUID companyId;
    private String companyName;
    private String orgSummary;
    private Map<String, String> glossary;
    private CopilotConfig copilotConfig;

    public static CopilotContextResDto fromEntity(Company company) {
        return CopilotContextResDto.builder()
                .companyId(company.getCompanyId())
                .companyName(company.getCompanyName())
                .orgSummary(company.getOrgSummary())
                .glossary(company.getGlossary() != null ? company.getGlossary() : Map.of())
                .copilotConfig(company.getCopilotConfig() != null ? company.getCopilotConfig() : CopilotConfig.defaults())
                .build();
    }
}

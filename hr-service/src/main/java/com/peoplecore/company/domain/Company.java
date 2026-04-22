package com.peoplecore.company.domain;

import com.peoplecore.company.domain.copilot.CopilotConfig;
import com.peoplecore.company.domain.copilot.CopilotConfigJsonConverter;
import com.peoplecore.company.domain.copilot.GlossaryJsonConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;
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

//    AI Copilot: 회사 개요 (시스템 프롬프트에 주입되는 업종·규모 맥락)
    @Column(columnDefinition = "TEXT")
    private String orgSummary;

//    AI Copilot: 회사 전용 용어 사전 (예: expense → "지출결의")
    @Convert(converter = GlossaryJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, String> glossary;

//    AI Copilot: 민감도 라우팅·기능 비활성화·로컬 전용 모드 등 운영 정책
    @Convert(converter = CopilotConfigJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    private CopilotConfig copilotConfig;


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

//    AI Copilot: 회사 개요 수정
    public void updateOrgSummary(String orgSummary){
        this.orgSummary = orgSummary;
    }

//    AI Copilot: 용어 사전 수정 (전체 교체)
    public void updateGlossary(Map<String, String> glossary){
        this.glossary = glossary;
    }

//    AI Copilot: 운영 정책 수정 (전체 교체)
    public void updateCopilotConfig(CopilotConfig copilotConfig){
        this.copilotConfig = copilotConfig;
    }
}

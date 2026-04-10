package com.peoplecore.company.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.company.domain.Company;
import com.peoplecore.company.domain.CompanyStatus;
import com.peoplecore.company.domain.ContractType;
import com.peoplecore.company.dtos.CompanyCreateReqDto;
import com.peoplecore.company.dtos.CompanyResDto;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.department.service.DepartmentService;
import com.peoplecore.event.CompanyCreateEvent;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.grade.service.GradeService;
import com.peoplecore.pay.service.InsuranceJobTypesService;
import com.peoplecore.pay.service.InsuranceRatesService;
import com.peoplecore.pay.service.PayItemsService;
import com.peoplecore.pay.service.PaySettingsService;
import com.peoplecore.title.service.TitleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Slf4j
@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final DepartmentService departmentService;
    private final GradeService gradeService;
    private final TitleService titleService;
    private final InsuranceJobTypesService insuranceJobTypesService;
    private final PayItemsService payItemsService;
    private final SuperAdminAccountService superAdminAccountService;
    private final InsuranceRatesService insuranceRatesService;
    private final PaySettingsService paySettingsService;
    private final CollaborationClient collaborationClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public CompanyService(CompanyRepository companyRepository, DepartmentService departmentService, GradeService gradeService, TitleService titleService, InsuranceJobTypesService insuranceJobTypesService, PayItemsService payItemsService, SuperAdminAccountService superAdminAccountService, InsuranceRatesService insuranceRatesService, PaySettingsService paySettingsService, CollaborationClient collaborationClient, KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.companyRepository = companyRepository;
        this.departmentService = departmentService;
        this.gradeService = gradeService;
        this.titleService = titleService;
        this.insuranceJobTypesService = insuranceJobTypesService;
        this.payItemsService = payItemsService;
        this.superAdminAccountService = superAdminAccountService;
        this.insuranceRatesService = insuranceRatesService;
        this.paySettingsService = paySettingsService;
        this.collaborationClient = collaborationClient;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    //    1. 회사 등록 + 기본데이터 세팅 + superAdmin 생성
    @Transactional
    public CompanyResDto createCompany(CompanyCreateReqDto reqDto) {

        if (companyRepository.existsByCompanyIp(reqDto.getCompanyIp())) {
            throw new CustomException(ErrorCode.COMPANY_IP_DUPLICATE);
        }

        if (!reqDto.getContractEndAt().isAfter(reqDto.getContractStartAt())) {
            throw new CustomException(ErrorCode.INVALID_CONTRACT_DATE);
        }


//        회사저장(PENDING상태)
        Company company = Company.builder()
                .companyName(reqDto.getCompanyName())
                .foundedAt(reqDto.getFoundedAt())
                .companyIp(reqDto.getCompanyIp())
                .contractStartAt(reqDto.getContractStartAt())
                .contractEndAt(reqDto.getContractEndAt())
                .contractType(reqDto.getContractType())
                .maxEmployees(reqDto.getMaxEmployees())
                .build();

        companyRepository.save(company);

        // 각 도메인 기본 데이터 세팅
        departmentService.initDefault(company);
        gradeService.initDefault(company);
        titleService.initDefault(company);
        insuranceJobTypesService.initDefault(company);
        payItemsService.initDefault(company);
        insuranceRatesService.initDefault(company);
        paySettingsService.initDefault(company);


        // superAdmin 계정 생성
        superAdminAccountService.createSuperAdmin(company, reqDto);

        try {
            collaborationClient.initDefaultFormFolder(company.getCompanyId());
        } catch (Exception e) {
            log.warn("양식 폴더 초기화 실패, Kafka 재시도 발행: {}", e.getMessage());
            try {
                String message = objectMapper.writeValueAsString(new CompanyCreateEvent(company.getCompanyId()));
                kafkaTemplate.send("company-folder-init", String.valueOf(company.getCompanyId()), message);
            } catch (JsonProcessingException ex) {
                log.error("카프카 직렬화 실패 ");
            }
        }

        company.changeStatus(CompanyStatus.ACTIVE);
        companyRepository.save(company);


        return CompanyResDto.fromEntity(company);
    }

    //    2. 회사 단일/목록 조회
    public CompanyResDto getCompany(UUID companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));
        return CompanyResDto.fromEntity(company);
    }

    public List<CompanyResDto> getCompanies(CompanyStatus companyStatus) {
        List<Company> companies = (companyStatus != null) ? companyRepository.findByCompanyStatus(companyStatus) : companyRepository.findAll();

        return companies.stream().map(CompanyResDto::fromEntity).collect(Collectors.toList());
    }

    //    3. 회사상태변경
    @Transactional
    public CompanyResDto updateStatus(UUID companyId, CompanyStatus newStatus) {
        Company company = companyRepository.findById(companyId).orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

        validateStatusTransition(company.getCompanyStatus(), newStatus);
        company.changeStatus(newStatus);

        return CompanyResDto.fromEntity(company);

    }

    //    4. 계약연장
    @Transactional
    public CompanyResDto extendContract(UUID companyId, LocalDate newEndDate, Integer maxEmployees, ContractType contractType) {
        Company company = companyRepository.findById(companyId).orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

        company.extendContract(newEndDate, maxEmployees, contractType);

        return CompanyResDto.fromEntity(company);
    }


    //    상태 전이 검증
//    허용된 전이(변경 경로)만 가능하게.
    private void validateStatusTransition(CompanyStatus current, CompanyStatus target) {

        Boolean valid = switch (current) {
            case PENDING -> target == CompanyStatus.ACTIVE;
            case ACTIVE -> target == CompanyStatus.EXPIRED || target == CompanyStatus.SUSPENDED;
            case SUSPENDED -> target == CompanyStatus.ACTIVE;
            case EXPIRED -> target == CompanyStatus.ACTIVE;
        };
        if (!valid) {
            throw new CustomException(ErrorCode.INVALID_STATUS_TRANSITION);
        }

    }
}

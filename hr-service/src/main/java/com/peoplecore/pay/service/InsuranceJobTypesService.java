package com.peoplecore.pay.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.InsuranceJobTypes;
import com.peoplecore.pay.dtos.InsuranceJobTypesReqDto;
import com.peoplecore.pay.dtos.InsuranceJobTypesResDto;
import com.peoplecore.pay.repository.InsuranceJobTypesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InsuranceJobTypesService {

    private final InsuranceJobTypesRepository insuranceJobTypesRepository;
    private final CompanyRepository companyRepository;
    private final EmployeeRepository employeeRepository;

    @Autowired
    public InsuranceJobTypesService(InsuranceJobTypesRepository insuranceJobTypesRepository, CompanyRepository companyRepository, EmployeeRepository employeeRepository) {
        this.insuranceJobTypesRepository = insuranceJobTypesRepository;
        this.companyRepository = companyRepository;
        this.employeeRepository = employeeRepository;
    }

//    산재보험 업종 목록 조회
    public List<InsuranceJobTypesResDto> getJobTypes(UUID companyId){
        return insuranceJobTypesRepository.findByCompany_CompanyIdOrderByJobTypesIdAsc(companyId)
                .stream()
                .map(InsuranceJobTypesResDto::fromEntity)
                .toList();
    }

//    산재보험 업종 추가
    @Transactional
    public InsuranceJobTypesResDto createJobType(UUID companyId, InsuranceJobTypesReqDto reqDto){
        Company company = companyRepository.findById(companyId).orElseThrow(()-> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

//        동일업종명 중복검사
        if(insuranceJobTypesRepository.findByCompany_CompanyIdAndName(companyId, reqDto.getName()).isPresent()){
            throw new CustomException(ErrorCode.INSURANCE_JOB_TYPE_DUPLICATE);
        }

        InsuranceJobTypes jobTypes = InsuranceJobTypes.builder()
                .company(company)
                .jobTypeName(reqDto.getName())
                .description(reqDto.getDesciption())
                .industrialAccidentRate(reqDto.getIndustrialAccidentRate())
                .isActive(true)
                .build();

        return InsuranceJobTypesResDto.fromEntity(insuranceJobTypesRepository.save(jobTypes));
    }

//     산재보험 업종 수정(요율, 업종명, 설명)
    @Transactional
    public InsuranceJobTypesResDto updateJobType(UUID companyId, Long jobTypesId, InsuranceJobTypesReqDto reqDto){
        InsuranceJobTypes jobTypes = findByIdAndCompany(jobTypesId, companyId);
        jobTypes.update(reqDto.getName(), reqDto.getDesciption(), reqDto.getIndustrialAccidentRate());
        return InsuranceJobTypesResDto.fromEntity(jobTypes);
    }

//    산재보험 업종 사용여부 토글
    @Transactional
    public InsuranceJobTypesResDto toggleActive(UUID companyId, Long jobTypesId){
        InsuranceJobTypes jobTypes = findByIdAndCompany(jobTypesId, companyId);

        jobTypes.toggleActive();

        return InsuranceJobTypesResDto.fromEntity(jobTypes);
    }

//     산재보험 업종 삭제
    @Transactional
    public void deleteJobType(UUID companyId, Long jobTypesId){
        InsuranceJobTypes jobTypes = insuranceJobTypesRepository.findByJobTypesIdAndCompany_CompanyId(jobTypesId, companyId).orElseThrow(()-> new CustomException(ErrorCode.INSURANCE_JOB_TYPE_NOT_FOUND));

//        사용중인지 검증
        if(employeeRepository.existsByJobTypes_JobTypesId(jobTypesId)){
            //        항목 사용여부 검증 -> 사용시 소프트딜리트
            jobTypes.softDelete();
        }
        insuranceJobTypesRepository.delete(jobTypes);
    }



    private InsuranceJobTypes findByIdAndCompany(Long jobTypesId, UUID companyId){
        return insuranceJobTypesRepository.findByJobTypesIdAndCompany_CompanyId(jobTypesId, companyId).orElseThrow(()-> new CustomException(ErrorCode.INSURANCE_JOB_TYPE_NOT_FOUND));
    }

//    //superAdmin 계정 생성시 초기값
//    public void initDefault(Company company) {
//        insuranceJobTypesRepository.save(
//                InsuranceJobTypes.builder()
//                        .company(company)
//                        .jobTypeName("기본업종")
//                        .description("일반 사무직")
//                        .industrialAccidentRate(new BigDecimal("0.0070"))
//                        .isActive(true)
//                        .build()
//        );

    public void initDefault(Company company) {
        List<InsuranceJobTypes> defaults = List.of(
                // 사무직 (기본 — 신규 사원 매핑 안 됐을 때 fallback 가능)
                buildJobType(company, "기본업종",         "0.0070", "일반 사무직 (미분류 사원 기본값)"),

                // 1차 산업
                buildJobType(company, "농업",             "0.0200", "농업, 축산업"),
                buildJobType(company, "임업",             "0.0570", "임업"),
                buildJobType(company, "어업",             "0.0280", "어업"),
                buildJobType(company, "광업",             "0.0570", "광업"),

                // 제조업
                buildJobType(company, "제조업(경공업)",   "0.0100", "식료품, 섬유, 의복 등"),
                buildJobType(company, "제조업(중공업)",   "0.0150", "금속, 기계, 자동차 등"),
                buildJobType(company, "제조업(화학)",     "0.0140", "석유화학, 화학제품"),

                // 건설/운수
                buildJobType(company, "건설업",           "0.0370", "토목, 건축, 인테리어"),
                buildJobType(company, "운수업",           "0.0150", "육상/해상/항공 운송"),
                buildJobType(company, "창고업",           "0.0090", "창고 및 보관"),

                // 서비스업
                buildJobType(company, "도소매업",         "0.0090", "도매 및 소매"),
                buildJobType(company, "음식점업",         "0.0100", "음식 및 음료서비스"),
                buildJobType(company, "숙박업",           "0.0100", "호텔, 펜션 등"),
                buildJobType(company, "교육서비스업",     "0.0080", "학원, 교육 기관"),
                buildJobType(company, "의료업",           "0.0070", "병원, 의료 서비스"),

                // 사무/IT/금융
                buildJobType(company, "IT/소프트웨어",    "0.0070", "소프트웨어 개발, 정보서비스"),
                buildJobType(company, "금융/보험업",      "0.0070", "금융, 보험, 회계"),

                // 기타
                buildJobType(company, "기타 서비스업",    "0.0090", "미분류 서비스업")
        );

        insuranceJobTypesRepository.saveAll(defaults);
    }

    // 업종 entity 빌더 헬퍼
    private InsuranceJobTypes buildJobType(Company company, String name, String rate, String description) {
        return InsuranceJobTypes.builder()
                .company(company)
                .jobTypeName(name)
                .description(description)
                .industrialAccidentRate(new BigDecimal(rate))
                .isActive(true)
                .build();
    }



}


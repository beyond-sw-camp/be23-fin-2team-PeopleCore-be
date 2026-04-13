package com.peoplecore.pay.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.InsuranceJobTypes;
import com.peoplecore.pay.dtos.InsuranceJobTypesReqDto;
import com.peoplecore.pay.dtos.InsuranceJobTypesResDto;
import com.peoplecore.pay.dtos.InsuranceRatesResDto;
import com.peoplecore.pay.repository.InsuranceJobTypesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
                .name(reqDto.getName())
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




    //superAdmin 계정 생성시 초기값
    public void initDefault(Company company) {
        insuranceJobTypesRepository.save(
                InsuranceJobTypes.builder()
                        .company(company)
                        .name("기본업종")
                        .description("일반 사무직")
                        .industrialAccidentRate(new BigDecimal("0.0070"))
                        .isActive(true)
                        .build()
        );
    }

    private InsuranceJobTypes findByIdAndCompany(Long jobTypesId, UUID companyId){
        return insuranceJobTypesRepository.findByJobTypesIdAndCompany_CompanyId(jobTypesId, companyId).orElseThrow(()-> new CustomException(ErrorCode.INSURANCE_JOB_TYPE_NOT_FOUND));
    }


}

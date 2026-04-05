package com.peoplecore.pay.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.InsuranceJobTypes;
import com.peoplecore.pay.dtos.InsuranceJobTypesReqDto;
import com.peoplecore.pay.dtos.InsuranceJobTypesResDto;
import com.peoplecore.pay.repository.InsuranceJobTypesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InsuranceJobTypesService {

    private final InsuranceJobTypesRepository insuranceJobTypesRepository;
    private final CompanyRepository companyRepository;

    @Autowired
    public InsuranceJobTypesService(InsuranceJobTypesRepository insuranceJobTypesRepository, CompanyRepository companyRepository) {
        this.insuranceJobTypesRepository = insuranceJobTypesRepository;
        this.companyRepository = companyRepository;
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



    //superAdmin 계정 생성시 초기값
    public void initDefault(Company company) {
        insuranceJobTypesRepository.save(
            InsuranceJobTypes.builder()
                    .company(company)
                    .name("기본업종")
                    .isActive(true)
                    .build()
        );
    }
}

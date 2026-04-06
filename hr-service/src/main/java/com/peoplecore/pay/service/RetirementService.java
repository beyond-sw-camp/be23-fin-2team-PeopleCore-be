package com.peoplecore.pay.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.RetirementSettings;
import com.peoplecore.pay.dtos.RetirementSettingsReqDto;
import com.peoplecore.pay.dtos.RetirementSettingsResDto;
import com.peoplecore.pay.enums.PensionType;
import com.peoplecore.pay.repository.RetirementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RetirementService {

    private final RetirementRepository retirementRepository;
    private final CompanyRepository companyRepository;

    @Autowired
    public RetirementService(RetirementRepository retirementRepository, CompanyRepository companyRepository) {
        this.retirementRepository = retirementRepository;
        this.companyRepository = companyRepository;
    }

//    퇴직연금설정 조회
    public RetirementSettingsResDto getRetirementSettings(UUID companyId){
        RetirementSettings settings = retirementRepository.findByCompany_CompanyId(companyId).orElse(null);

//        퇴직연금 설정이 없으면 퇴직금(default) 반환
        if(settings == null){
            return RetirementSettingsResDto.builder()
                    .pensionType(PensionType.severance)
                    .build();
        }

        return RetirementSettingsResDto.fromEntity(settings);
    }

//    퇴직연금설정 저장/수정
    @Transactional
    public RetirementSettingsResDto saveRetirementSettings(UUID companyId, RetirementSettingsReqDto reqDto){

        if (reqDto.getPensionType() == PensionType.DB || reqDto.getPensionType() == PensionType.DB_DC){
            if(reqDto.getPensionProvider() == null || reqDto.getPensionProvider().isBlank()){
                throw new CustomException(ErrorCode.RETIREMENT_PROVIDER_REQUIRED);
            }
        }

        RetirementSettings settings = retirementRepository.findByCompany_CompanyId(companyId).orElse(null);

//        최초 설정시
        if(settings == null){
            Company company = companyRepository.findById(companyId).orElseThrow(()-> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

            settings = RetirementSettings.builder()
                    .company(company)
                    .pensionType(reqDto.getPensionType())
                    .pensionProvider(reqDto.getPensionProvider())
                    .pensionAccount(reqDto.getPensionAccount())
                    .build();

            retirementRepository.save(settings);

        } else {
//            수정시
            settings.update(reqDto.getPensionType(), reqDto.getPensionProvider(),reqDto.getPensionAccount());
        }
        return RetirementSettingsResDto.fromEntity(settings);

    }

//    private boolean needsProviderInfo(PensionType type) {
//        return type == PensionType.DB || type == PensionType.DB_DC;
//    }
}

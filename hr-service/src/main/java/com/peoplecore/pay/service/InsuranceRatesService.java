package com.peoplecore.pay.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.InsuranceRates;
import com.peoplecore.pay.dtos.InsuranceRatesEmployerReqDto;
import com.peoplecore.pay.dtos.InsuranceRatesResDto;
import com.peoplecore.pay.repository.InsuranceRatesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InsuranceRatesService {

    private final InsuranceRatesRepository insuranceRatesRepository;
    private final CompanyRepository companyRepository;

    @Autowired
    public InsuranceRatesService(InsuranceRatesRepository insuranceRatesRepository, CompanyRepository companyRepository) {
        this.insuranceRatesRepository = insuranceRatesRepository;
        this.companyRepository = companyRepository;
    }


//    현재연도 보험요율 조회
    public InsuranceRatesResDto getCurrentRates(UUID companyId){
        int currentYear = LocalDate.now().getYear();
        InsuranceRates rates = findByCompanyAndYear(companyId, currentYear);
        return InsuranceRatesResDto.fromEntity(rates);
    }

    //    현재연도 보험요율 조회
    public InsuranceRatesResDto getRatesByYear(UUID companyId, Integer year){
        InsuranceRates rates = findByCompanyAndYear(companyId, year);
        return InsuranceRatesResDto.fromEntity(rates);
    }

//    고용보험 사업주 요율 수정
    @Transactional
    public InsuranceRatesResDto updateEmployerRate(UUID companyId, InsuranceRatesEmployerReqDto reqDto){

        int currentYear = LocalDate.now().getYear();
        InsuranceRates rates = findByCompanyAndYear(companyId, currentYear);

        rates.updateEmployerRate(reqDto.getEmploymentInsuranceEmployer());

        return InsuranceRatesResDto.fromEntity(rates);

    }



    private InsuranceRates findByCompanyAndYear(UUID companyId, Integer currentYear){
        return insuranceRatesRepository.findByCompany_CompanyIdAndYear(companyId,currentYear).orElseThrow(()-> new CustomException(ErrorCode.INSURANCE_RATES_NOT_FOUND));
    }


    @Transactional
    //회사 생성시 초기값
    public void initDefault(Company company) {
        InsuranceRates defaultRates = InsuranceRates.builder()
                .company(company)
                .year(LocalDate.now().getYear())
                .nationalPension(new BigDecimal("0.0450"))          // 4.5%
                .healthInsurance(new BigDecimal("0.03545"))         // 3.545%
                .longTermCare(new BigDecimal("0.1295"))             // 건강보험의 12.95%
                .employmentInsurance(new BigDecimal("0.0090"))      // 근로자 0.9%
                .employmentInsuranceEmployer(new BigDecimal("0.0090"))  // 사업주 0.9% (기본)
                .validFrom(LocalDate.of(LocalDate.now().getYear(), 1, 1))
                .pensionUpperLimit(6_170_000L)
                .pensionLowerLimit(390_000L)
                .build();

        insuranceRatesRepository.save(defaultRates);
    }


}

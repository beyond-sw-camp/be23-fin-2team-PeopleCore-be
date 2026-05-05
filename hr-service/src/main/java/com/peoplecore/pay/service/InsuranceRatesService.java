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
    //회사 생성시 초기값 (24~26년)
    public void initDefault(Company company) {
        // 2024년 — 7월 이후 적용된 상/하한액으로 시드 (1~6월은 안내로 처리)
        saveYearlyDefault(company, 2024,
                "0.0450",     // 국민연금
                "0.03545",    // 건강보험
                "0.1295",     // 장기요양 (건강보험의 12.95%)
                "0.0090",     // 고용보험 근로자
                "0.0090",     // 고용보험 사업주 (회사 기본값)
                6_170_000L, 390_000L);   // 2024-07~ 적용

        // 2025년 — 요율 동결, 7월 이후 상/하한액으로 시드
        saveYearlyDefault(company, 2025,
                "0.0450",
                "0.03545",
                "0.1295",
                "0.0090",
                "0.0090",
                6_370_000L, 400_000L);   // 2025-07~ 적용

        // 2026년 — 요율 인상 확정, 상/하한액은 1~6월 기준
        saveYearlyDefault(company, 2026,
                "0.0475",     // 국민연금 4.5% → 4.75% (0.5%p 인상)
                "0.03595",    // 건강보험 3.545% → 3.595% (0.1%p 인상)
                "0.1314",     // 장기요양 12.95% → 13.14%
                "0.0090",     // 고용보험 근로자
                "0.0090",     // 고용보험 사업주
                6_370_000L, 400_000L);
    }

    private void saveYearlyDefault(Company company,
                                   int year,
                                   String nationalPension,
                                   String healthInsurance,
                                   String longTermCare,
                                   String employmentInsurance,
                                   String employmentInsuranceEmployer,
                                   long pensionUpperLimit,
                                   long pensionLowerLimit) {
        InsuranceRates rates = InsuranceRates.builder()
                .company(company)
                .year(year)
                .nationalPension(new BigDecimal(nationalPension))
                .healthInsurance(new BigDecimal(healthInsurance))
                .longTermCare(new BigDecimal(longTermCare))
                .employmentInsurance(new BigDecimal(employmentInsurance))
                .employmentInsuranceEmployer(new BigDecimal(employmentInsuranceEmployer))
                .validFrom(LocalDate.of(year, 1, 1))
                .validTo(LocalDate.of(year, 12, 31))
                .pensionUpperLimit(pensionUpperLimit)
                .pensionLowerLimit(pensionLowerLimit)
                .build();
        insuranceRatesRepository.save(rates);
    }

}

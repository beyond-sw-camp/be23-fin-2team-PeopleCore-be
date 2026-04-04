package com.peoplecore.pay.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.pay.domain.InsuranceJobTypes;
import com.peoplecore.pay.repository.InsuranceJobTypesRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class InsuranceJobTypesService {

    private final InsuranceJobTypesRepository insuranceJobTypesRepository;

    public InsuranceJobTypesService(InsuranceJobTypesRepository insuranceJobTypesRepository) {
        this.insuranceJobTypesRepository = insuranceJobTypesRepository;
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

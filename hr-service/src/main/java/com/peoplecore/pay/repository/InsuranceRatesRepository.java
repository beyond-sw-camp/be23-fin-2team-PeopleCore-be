package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.InsuranceRates;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InsuranceRatesRepository extends JpaRepository<InsuranceRates, Long> {

//    특정연도 요율
    Optional<InsuranceRates> findByCompany_CompanyIdAndYear(UUID companyId, Integer year);

}

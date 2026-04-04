package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.InsuranceJobTypes;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InsuranceJobTypesRepository extends JpaRepository<InsuranceJobTypes, Long> {

    Optional<InsuranceJobTypes> findByCompany_CompanyIdAndName(UUID companyId, String name);
}

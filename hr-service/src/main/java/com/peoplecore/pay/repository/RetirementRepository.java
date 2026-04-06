package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.RetirementSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RetirementRepository extends JpaRepository<RetirementSettings, Long> {
    Optional<RetirementSettings> findByCompany_CompanyId(UUID companyId);
}

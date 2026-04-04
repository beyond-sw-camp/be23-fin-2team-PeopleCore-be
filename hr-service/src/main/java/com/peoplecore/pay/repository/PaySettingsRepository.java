package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.CompanyPaySettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaySettingsRepository extends JpaRepository<CompanyPaySettings, Long> {
    Optional<CompanyPaySettings> findByCompany_CompanyId(UUID companyId);
}

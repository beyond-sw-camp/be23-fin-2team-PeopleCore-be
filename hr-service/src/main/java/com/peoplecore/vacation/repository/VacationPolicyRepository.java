package com.peoplecore.vacation.repository;

import com.peoplecore.vacation.entity.VacationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VacationPolicyRepository extends JpaRepository<VacationPolicy, Long> {
    Optional<VacationPolicy> findByCompanyId(UUID companyId);
}



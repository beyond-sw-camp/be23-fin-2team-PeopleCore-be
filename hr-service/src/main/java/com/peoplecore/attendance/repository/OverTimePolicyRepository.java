package com.peoplecore.attendance.repository;

import com.peoplecore.attendance.entity.OvertimePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OverTimePolicyRepository extends JpaRepository<OvertimePolicy, Long> {
    Optional<OvertimePolicy> findByCompany_CompanyId(UUID companyId);
}

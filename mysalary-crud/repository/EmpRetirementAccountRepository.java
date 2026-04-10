package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.EmpRetirementAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmpRetirementAccountRepository extends JpaRepository<EmpRetirementAccount, Long> {

    Optional<EmpRetirementAccount> findByEmpIdAndCompany_CompanyId(Long empId, UUID companyId);
}

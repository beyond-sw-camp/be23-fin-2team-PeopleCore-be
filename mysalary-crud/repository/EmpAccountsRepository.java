package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.EmpAccounts;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmpAccountsRepository extends JpaRepository<EmpAccounts, Long> {

    Optional<EmpAccounts> findByEmployee_EmpIdAndCompany_CompanyId(Long empId, UUID companyId);

    Optional<EmpAccounts> findByEmpAccountIdAndEmployee_EmpIdAndCompany_CompanyId(
            Long empAccountId, Long empId, UUID companyId);
}

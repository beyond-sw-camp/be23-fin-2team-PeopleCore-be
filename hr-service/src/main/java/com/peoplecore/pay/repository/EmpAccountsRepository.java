package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.EmpAccounts;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmpAccountsRepository extends JpaRepository<EmpAccounts, Long> {

//    사원 계좌 단건
    Optional<EmpAccounts> findByEmployee_EmpIdAndCompany_CompanyId(Long empId, UUID companyId);

//    사원 ID + 계좌 ID (수정시)
    Optional<EmpAccounts> findByEmpAccountIdAndEmployee_EmpIdAndCompany_CompanyId(Long empAccountId, Long empId, UUID companyId);

//    배치 조회
    List<EmpAccounts> findByEmployee_EmpIdInAndCompany_CompanyId(List<Long> empIds, UUID companyId);



}

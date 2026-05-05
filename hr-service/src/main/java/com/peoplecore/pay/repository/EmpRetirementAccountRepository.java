package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.EmpRetirementAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.swing.text.html.Option;
import java.util.*;

public interface EmpRetirementAccountRepository extends JpaRepository<EmpRetirementAccount, Long> {

    Optional<EmpRetirementAccount> findByEmployee_EmpIdAndCompany_CompanyId(Long empId, UUID companyId);

    Arrays findAllByCompany_CompanyIdAndEmployee_EmpIdIn(UUID companyCompanyId, Collection<Long> employeeEmpIds);

//    사원의 사업자/DC계좌 정보 조회용 (사원당 1개)
    List<EmpRetirementAccount> findAllByCompany_CompanyIdAndEmployee_EmpIdIn(UUID companyId, List<Long> empIds);
}

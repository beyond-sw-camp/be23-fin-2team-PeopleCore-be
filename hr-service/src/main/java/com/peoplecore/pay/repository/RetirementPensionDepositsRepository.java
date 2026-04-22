package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.RetirementPensionDeposits;
import com.peoplecore.pay.enums.DepStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RetirementPensionDepositsRepository extends JpaRepository<RetirementPensionDeposits, Long> {

//    급여대장에 이미 적립된 내역 존재 여부(중복 산입 방지)
    boolean existsByPayrollRunIdAndEmpId(Long payrollRunId, Long empId);

    List<RetirementPensionDeposits> findByEmpIdAndCompany_CompanyIdAndDepStatus(Long empId, UUID companyId, DepStatus depStatus);
}

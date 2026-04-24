package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.RetirementPensionDeposits;
import com.peoplecore.pay.enums.DepStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RetirementPensionDepositsRepository extends JpaRepository<RetirementPensionDeposits, Long>, PensionDepositQueryRepository {

//    급여대장에 이미 적립된 내역 존재 여부(중복 산입 방지)
    boolean existsByPayrollRun_PayrollRunIdAndEmployee_EmpId(Long payrollRunId, Long empId);

    List<RetirementPensionDeposits> findByEmployee_EmpIdAndCompany_CompanyIdAndDepStatus(Long empId, UUID companyId, DepStatus depStatus);

    boolean existsByEmployee_EmpIdAndPayYearMonthAndDepStatus(
            Long empId, String payYearMonth, com.peoplecore.pay.enums.DepStatus depStatus);

    Optional<RetirementPensionDeposits> findByDepIdAndCompany_CompanyId(Long depId, UUID companyId);


    Optional<RetirementPensionDeposits> findTopByEmployee_EmpIdAndCompany_CompanyIdAndDepStatusOrderByDepositDateDesc(
            Long empId, UUID companyId, DepStatus depStatus);
}
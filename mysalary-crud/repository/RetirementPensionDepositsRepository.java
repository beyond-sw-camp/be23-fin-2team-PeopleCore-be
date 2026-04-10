package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.RetirementPensionDeposits;
import com.peoplecore.pay.enums.DepStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RetirementPensionDepositsRepository extends JpaRepository<RetirementPensionDeposits, Long> {

    @Query("SELECT COALESCE(SUM(d.depositAmount), 0) FROM RetirementPensionDeposits d " +
           "WHERE d.empId = :empId AND d.company.companyId = :companyId AND d.depStatus = :status")
    Long sumDepositAmountByEmpIdAndCompanyAndStatus(
            @Param("empId") Long empId,
            @Param("companyId") UUID companyId,
            @Param("status") DepStatus status);

    Optional<RetirementPensionDeposits> findTopByEmpIdAndCompany_CompanyIdOrderByDepositDateDesc(
            Long empId, UUID companyId);

    Optional<RetirementPensionDeposits> findTopByEmpIdAndCompany_CompanyIdAndDepStatusOrderByDepositDateDesc(
            Long empId, UUID companyId, DepStatus status);
}

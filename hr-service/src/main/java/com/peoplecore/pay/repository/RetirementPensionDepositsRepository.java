package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.EmpRetirementAccount;
import com.peoplecore.pay.domain.RetirementPensionDeposits;
import com.peoplecore.pay.enums.DepStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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


    // 퇴직연금 엑셀 다운로드용 -  row 조회용 (사원 × 월 단위)
    @Query("""
    SELECT d FROM RetirementPensionDeposits d
    JOIN FETCH d.employee e
    LEFT JOIN FETCH e.dept
    WHERE d.company.companyId = :companyId
      AND d.payYearMonth >= :fromYm
      AND d.payYearMonth <= :toYm
      AND d.depStatus = com.peoplecore.pay.enums.DepStatus.COMPLETED
    ORDER BY e.empNum ASC, d.payYearMonth ASC
    """)
    List<RetirementPensionDeposits> findCompletedByCompanyAndYearMonthBetween(
            @Param("companyId") UUID companyId,
            @Param("fromYm") String fromYm,
            @Param("toYm") String toYm);

    List<RetirementPensionDeposits> findByPayrollRun_PayrollRunIdAndDepStatus(
            Long payrollRunId, DepStatus depStatus);

}
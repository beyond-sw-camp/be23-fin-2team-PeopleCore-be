package com.peoplecore.salarycontract.repository;


import com.peoplecore.salarycontract.domain.SalaryContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface SalaryContractRepository extends JpaRepository<SalaryContract, Long>, SalaryContractRepositoryCustom {

    List<SalaryContract>findByCompanyIdAndEmployee_EmpIdAndDeletedAtIsNullOrderByApplyFromDesc(UUID companyId, Long empId);

    Optional<SalaryContract> findTopByEmployee_EmpIdOrderByApplyFromDesc(Long employeeId);

    /*  사원ID 리스트 + 기간(periodStart ~ periodEnd) 안에 적용된 유효 계약을 한 번의 쿼리로 조회.
     * - year 미지정: today ~ today
     * - year 지정:    YYYY-01-01 ~ YYYY-12-31
     * 정렬: applyFrom DESC → empId별 첫 번째가 최신 적용 계약
     */
    @Query("""
            SELECT c FROM SalaryContract c
            WHERE c.companyId = :companyId
              AND c.employee.empId IN :empIds
              AND c.deletedAt IS NULL
              AND (c.applyFrom IS NULL OR c.applyFrom <= :periodEnd)
              AND (c.applyTo IS NULL OR c.applyTo >= :periodStart)
            ORDER BY c.applyFrom DESC
            """)
    List<SalaryContract> findActiveContractsByEmpIds(
            @Param("companyId") UUID companyId,
            @Param("empIds") List<Long> empIds,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd
    );

}

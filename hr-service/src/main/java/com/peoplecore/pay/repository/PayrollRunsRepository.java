package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.PayrollRuns;
import com.peoplecore.pay.enums.PayrollStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayrollRunsRepository extends JpaRepository<PayrollRuns, Long> {

    Optional<PayrollRuns> findByCompany_CompanyIdAndPayYearMonth(UUID companyId, String payYearMonth);

//    정산기간 내 지급완료 급여대장 조회 (기공제액 산출용)
    List<PayrollRuns> findByCompany_CompanyIdAndPayrollStatusAndPayYearMonthBetween(
            UUID companyId, PayrollStatus payrollStatus, String fromMonth, String toMonth);
}

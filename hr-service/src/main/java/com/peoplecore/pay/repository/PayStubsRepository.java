package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.PayStubs;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayStubsRepository extends JpaRepository<PayStubs, Long> {

    /**
     * 특정 사원의 연도별 명세서 목록.
     * payYearMonth 가 "YYYY-MM" 형식이라 startsWith(year) 로 필터.
     */
    List<PayStubs> findByEmpIdAndCompany_CompanyIdAndPayYearMonthStartingWithOrderByPayYearMonthDesc(
            Long empId, UUID companyId, String payYearMonthPrefix);

    /** 본인 소유 검증 포함 단건 조회 */
    Optional<PayStubs> findByPayStubsIdAndEmpIdAndCompany_CompanyId(
            Long payStubsId, Long empId, UUID companyId);

    boolean existsByEmpIdAndPayrollRunId(Long empId, Long payrollRunId);

}


package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.PayStubs;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayStubsRepository extends JpaRepository<PayStubs, Long> {

    Optional<PayStubs> findByPayStubsIdAndEmpIdAndCompany_CompanyId(Long payStubsId, Long empId, UUID companyId);

    List<PayStubs> findByEmpIdAndCompany_CompanyIdAndPayYearMonthStartingWithOrderByPayYearMonthDesc(
            Long empId, UUID companyId, String yearPrefix);

    Optional<PayStubs> findTopByEmpIdAndCompany_CompanyIdOrderByPayYearMonthDesc(Long empId, UUID companyId);

    List<PayStubs> findByEmpIdAndCompany_CompanyIdAndPayYearMonthInOrderByPayYearMonthDesc(
            Long empId, UUID companyId, List<String> payYearMonths);
}

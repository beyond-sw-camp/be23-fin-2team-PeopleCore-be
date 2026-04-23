package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.InsuranceSettlement;
import com.peoplecore.pay.domain.PayrollRuns;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InsuranceSettlementRepository extends JpaRepository<InsuranceSettlement, Long> {

//    회사 + 연월 조회
    @Query("SELECT s FROM InsuranceSettlement s JOIN FETCH s.employee e JOIN FETCH e.dept WHERE s.company.companyId = :companyId AND s.payYearMonth = :payYearMonth ORDER BY e.empName ASC")
    List<InsuranceSettlement> findAllWithEmployee(@Param("companyId") UUID companyId,@Param("payYearMonth") String payYearMonth);


//    특정 급여대장의 전체 정산보험료
    List<InsuranceSettlement> findByPayrollRuns(PayrollRuns payrollRuns);


//    특정 정산 상세(단건)
    @Query("SELECT s FROM InsuranceSettlement s JOIN FETCH s.employee e JOIN FETCH e.dept JOIN FETCH e.grade JOIN FETCH e.title JOIN FETCH s.insuranceRates WHERE s.settlementId = :settlementId AND s.company.companyId = :companyId")
    Optional<InsuranceSettlement> findDetailById(@Param("settlementId") Long settlementId,@Param("companyId") UUID companyId);


//    해당 급여대장에 대한 정산 존재 여부
    boolean existsByPayrollRuns(PayrollRuns payrollRuns);


//    해당 월 정산 삭제 (재산정 시)
    void deleteByPayrollRuns(PayrollRuns payrollRuns);


//    정산보험료 급여반영용
    @Query("SELECT s FROM InsuranceSettlement s JOIN FETCH s.employee e JOIN FETCH s.company WHERE s.settlementId IN :ids AND s.company.companyId = :companyId")
    List<InsuranceSettlement> findAllByIdsAndCompany(
            @Param("ids") List<Long> ids,
            @Param("companyId") UUID companyId);

//    정산기간으로 조회 (합계 계산용 - 페이징X)
    @Query("SELECT s FROM InsuranceSettlement s JOIN FETCH s.employee e JOIN FETCH e.dept WHERE s.company.companyId = :companyId AND s.settlementFromMonth = :fromMonth AND s.settlementToMonth = :toMonth ORDER BY e.empName ASC")
    List<InsuranceSettlement> findAllByPeriod(
            @Param("companyId") UUID companyIdm,
            @Param("fromMonth") String fromMonth,
            @Param("toMonth") String toMonth);

    // 정산기간으로 페이지 조회 (테이블용 — 페이징 O)
    // JOIN FETCH + Page 사용 시 countQuery 별도 지정 필수
    @Query(value = "SELECT s FROM InsuranceSettlement s JOIN FETCH s.employee e JOIN FETCH e.dept " +
            "WHERE s.company.companyId = :companyId " +
            "AND s.settlementFromMonth = :fromMonth AND s.settlementToMonth = :toMonth " +
            "ORDER BY e.empName ASC",
            countQuery = "SELECT COUNT(s) FROM InsuranceSettlement s " +
                    "WHERE s.company.companyId = :companyId " +
                    "AND s.settlementFromMonth = :fromMonth AND s.settlementToMonth = :toMonth")
    Page<InsuranceSettlement> findPageByPeriod(
            @Param("companyId") UUID companyId,
            @Param("fromMonth") String fromMonth,
            @Param("toMonth") String toMonth,
            Pageable pageable);


    //    정산기간 존재여부
    boolean existsByCompany_CompanyIdAndSettlementFromMonthAndSettlementToMonth(UUID companyId, String fromMonth, String toMonth);

//    정산기간 삭제 (재산정시)
    void deleteByCompany_CompanyIdAndSettlementFromMonthAndSettlementToMonth(UUID companyId, String fromMonth, String toMonth);
}

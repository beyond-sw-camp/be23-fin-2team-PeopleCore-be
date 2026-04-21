package com.peoplecore.pay.repository;

import com.peoplecore.pay.approval.PayrollItemSummaryDto;
import com.peoplecore.pay.domain.PayrollDetails;
import com.peoplecore.pay.domain.PayrollRuns;
import com.peoplecore.pay.dtos.InsuranceDeductionSummary;
import com.peoplecore.pay.enums.PayItemType;
import com.peoplecore.pay.enums.PayrollStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PayrollDetailsRepository extends JpaRepository<PayrollDetails, Long> {


    //    기공제액 집계 : 사원별 + 항목별 공제 합산 (정산기간내 PAID 급여대장)
    @Query("SELECT pd.employee.empId AS empId, pd.payItemName AS payItemName, SUM(pd.amount) AS totalAmount " +
            "FROM PayrollDetails pd " +
            "WHERE pd.company.companyId = :companyId " +
            "AND pd.payrollRuns.payrollStatus = :status " +
            "AND pd.payrollRuns.payYearMonth BETWEEN :fromMonth AND :toMonth " +
            "AND pd.payItemType = :itemType " +
            "AND pd.payItemName IN :itemNames " +
            "GROUP BY pd.employee.empId, pd.payItemName")
    List<InsuranceDeductionSummary> sumDeductionsByEmpAndItem(
            @Param("companyId") UUID companyId,
            @Param("status") PayrollStatus status,
            @Param("fromMonth") String fromMonth,
            @Param("toMonth") String toMonth,
            @Param("itemType") PayItemType itemType,
            @Param("itemNames") List<String> itemNames);


    //    특정 급여대장의 전체 상세 조회
    List<PayrollDetails> findByPayrollRuns(PayrollRuns payrollRuns);

    //    특정 급여대장 + 특정 사원의 상세 조회
    List<PayrollDetails> findByPayrollRunsAndEmployee_EmpId(PayrollRuns payrollRuns, Long empId);

    //    급여항목 사용 여부 체크(삭제시)
    boolean existsByPayItems_PayItemId(Long payItemId);

    //    사원별 급여 상세
    List<PayrollDetails> findByPayrollRuns_PayrollRunId(Long payrollRunId);

    boolean existsByPayrollRunsAndEmployee_EmpIdAndIsOvertimePayTrue(PayrollRuns payrollRuns, Long empId);

    //    급여 지급 대상 사원 수
    @Query("SELECT COUNT(DISTINCT pd.employee.empId) FROM PayrollDetails pd " +
            "WHERE pd.payrollRuns.payrollRunId = :payrollRunId")
    long countDistinctEmployees(@Param("payrollRunId") Long payrollRunId);


    // 급여대장의 PayItem별 + 과세여부별 금액 합계
    @Query("SELECT new com.peoplecore.pay.approval.PayrollItemSummaryDto( " +
            "   pd.payItemName, pd.payItems.isTaxable, SUM(pd.amount) " +
            ") " +
            "FROM PayrollDetails pd " +
            "WHERE pd.payrollRuns.payrollRunId = :payrollRunId " +
            "GROUP BY pd.payItemName, pd.payItems.isTaxable")
    List<PayrollItemSummaryDto> summarizeByPayItem(@Param("payrollRunId") Long payrollRunId);

    @Query("SELECT new com.peoplecore.pay.approval.PayrollItemSummaryDto( " +
            "   pd.payItemName, pd.payItems.isTaxable, SUM(pd.amount) " +
            ") " +
            "FROM PayrollDetails pd " +
            "WHERE pd.payrollRuns.payrollRunId = :payrollRunId " +
            "  AND pd.payItemType = :payItemType " +
            "GROUP BY pd.payItemName, pd.payItems.isTaxable")
    List<PayrollItemSummaryDto> summarizeByPayItem(
            @Param("payrollRunId") Long payrollRunId,
            @Param("payItemType") PayItemType payItemType);
}
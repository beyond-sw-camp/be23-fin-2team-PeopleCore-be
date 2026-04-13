package com.peoplecore.pay.repository;

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

    boolean existsByPayItems_PayItemId(Long payItemId);

    List<PayrollDetails> findByPayrollRuns(PayrollRuns payrollRuns);

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

}

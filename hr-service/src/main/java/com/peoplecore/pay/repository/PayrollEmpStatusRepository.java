package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.PayrollEmpStatus;
import com.peoplecore.pay.enums.PayrollEmpStatusType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayrollEmpStatusRepository extends JpaRepository<PayrollEmpStatus, Long> {
    Optional<PayrollEmpStatus> findByPayrollRuns_PayrollRunIdAndEmployee_EmpId(Long payrollRunId, Long empId);

    List<PayrollEmpStatus> findByPayrollRuns_PayrollRunIdAndStatus(Long payrollRunId, PayrollEmpStatusType status);

    List<PayrollEmpStatus> findByPayrollRuns_PayrollRunId(Long payrollRunId);

    List<PayrollEmpStatus> findByPayrollRuns_PayrollRunIdAndApprovalDocId(Long runId, Long docId);

}

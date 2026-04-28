package com.peoplecore.pay.approval;

import com.peoplecore.event.PayrollApprovalDocCreatedEvent;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.PayrollEmpStatus;
import com.peoplecore.pay.domain.PayrollRuns;
import com.peoplecore.pay.enums.PayrollEmpStatusType;
import com.peoplecore.pay.repository.PayrollEmpStatusRepository;
import com.peoplecore.pay.repository.PayrollRunsRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class PayrollApprovalDocCreatedService {

    private final PayrollRunsRepository payrollRunsRepository;
    private final PayrollEmpStatusRepository payrollEmpStatusRepository;

    @Autowired
    public PayrollApprovalDocCreatedService(PayrollRunsRepository payrollRunsRepository, PayrollEmpStatusRepository payrollEmpStatusRepository) {
        this.payrollRunsRepository = payrollRunsRepository;
        this.payrollEmpStatusRepository = payrollEmpStatusRepository;
    }


    @Transactional
    public void applyDocCreated(PayrollApprovalDocCreatedEvent event) {
        if (event.getPayrollRunId() == null) {
            log.warn("[PayrollDocCreated] payrollRunId 누락 - docId={}", event.getApprovalDocId());
            return;
        }
        PayrollRuns run = payrollRunsRepository.findById(event.getPayrollRunId())
                .orElseThrow(() -> new CustomException(ErrorCode.PAYROLL_NOT_FOUND));
        // status 전이 + approvalDocId 저장
        run.submitApproval(event.getApprovalDocId());
        log.info("[PayrollDocCreated] payrollRunId={}, status={}, docId={}",
                run.getPayrollRunId(), run.getPayrollStatus(), event.getApprovalDocId());

        // CONFIRMED 사원들에게 docId 바인딩
        List<PayrollEmpStatus> confirmedEmps = payrollEmpStatusRepository
                .findByPayrollRuns_PayrollRunIdAndStatus(
                        run.getPayrollRunId(), PayrollEmpStatusType.CONFIRMED);
        for (PayrollEmpStatus pes : confirmedEmps) {
            pes.bindApprovalDoc(event.getApprovalDocId());
        }
    }

}

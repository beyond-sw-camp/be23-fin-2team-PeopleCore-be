package com.peoplecore.pay.approval;

import com.peoplecore.event.PayrollApprovalDocCreatedEvent;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.PayrollRuns;
import com.peoplecore.pay.repository.PayrollRunsRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PayrollApprovalDocCreatedService {

    private final PayrollRunsRepository payrollRunsRepository;


    public PayrollApprovalDocCreatedService(PayrollRunsRepository payrollRunsRepository) {
        this.payrollRunsRepository = payrollRunsRepository;
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
    }

}

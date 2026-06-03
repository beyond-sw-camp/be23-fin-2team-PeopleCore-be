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

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class PayrollApprovalDocCreatedService {

    private final PayrollRunsRepository payrollRunsRepository;
    private final PayrollEmpStatusRepository payrollEmpStatusRepository;
    private final PayrollApprovalSnapshotRepository snapshotRepository;

    @Autowired
    public PayrollApprovalDocCreatedService(PayrollRunsRepository payrollRunsRepository, PayrollEmpStatusRepository payrollEmpStatusRepository, PayrollApprovalSnapshotRepository snapshotRepository) {
        this.payrollRunsRepository = payrollRunsRepository;
        this.payrollEmpStatusRepository = payrollEmpStatusRepository;
        this.snapshotRepository = snapshotRepository;
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

//        결재 대상 사원 후보
        List<Long> selectedEmpIds = event.getSelectedEmpIds();
        List<PayrollEmpStatus> targets;

        if (selectedEmpIds != null && !selectedEmpIds.isEmpty()) {
            targets = payrollEmpStatusRepository.findByPayrollRuns_PayrollRunIdAndStatusAndEmployee_EmpIdIn(
                    run.getPayrollRunId(), PayrollEmpStatusType.CONFIRMED, selectedEmpIds);
        } else {
            targets = payrollEmpStatusRepository.findByPayrollRuns_PayrollRunIdAndStatus(run.getPayrollRunId(),PayrollEmpStatusType.CONFIRMED);
        }

        for (PayrollEmpStatus pes : targets) {
            if (pes.getApprovalDocId() == null) {
                pes.bindApprovalDoc(event.getApprovalDocId());
            }
        }

        log.info("[PayrollDocCreated] runId={}, docId={}, 바인딩 대상={}명, selected={}명", run.getPayrollRunId(), event.getApprovalDocId(), targets.size(), selectedEmpIds != null ? selectedEmpIds.size() : -1);

        // 스냅샷 저장
        if (event.getHtmlContent() != null && !event.getHtmlContent().isBlank()) {
            snapshotRepository.save(PayrollApprovalSnapshot.builder()
                    .approvalDocId(event.getApprovalDocId())
                    .approvalType(ApprovalFormType.SALARY)
                    .payrollRunId(run.getPayrollRunId())
                    .companyId(run.getCompany().getCompanyId())
                    .htmlSnapshot(event.getHtmlContent())
                    .createdAt(LocalDateTime.now())
                    .build());
            log.info("[PayrollDocCreated] 스냅샷 저장 - docId={}, htmlLen={}",
                    event.getApprovalDocId(), event.getHtmlContent().length());
        } else {
            log.warn("[PayrollDocCreated] htmlContent 없음 — 스냅샷 미저장. docId={}",
                    event.getApprovalDocId());
        }
    }

}

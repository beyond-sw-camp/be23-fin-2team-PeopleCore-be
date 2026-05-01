package com.peoplecore.pay.approval;

import com.peoplecore.event.PayrollApprovalDocCreatedEvent;
import com.peoplecore.event.SeveranceApprovalDocCreatedEvent;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.PayrollRuns;
import com.peoplecore.pay.domain.SeverancePays;
import com.peoplecore.pay.repository.PayrollRunsRepository;
import com.peoplecore.pay.repository.SeverancePaysRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class SeveranceApprovalDocCreatedService {

    private final SeverancePaysRepository severancePaysRepository;
    private final PayrollApprovalSnapshotRepository snapshotRepository;

    @Autowired
    public SeveranceApprovalDocCreatedService(SeverancePaysRepository severancePaysRepository, PayrollApprovalSnapshotRepository snapshotRepository) {
        this.severancePaysRepository = severancePaysRepository;
        this.snapshotRepository = snapshotRepository;
    }


    @Transactional
    public void applyDocCreated(SeveranceApprovalDocCreatedEvent event) {
        if (event.getSevId() == null) {
            log.warn("[SeveranceDocCreated] sevId 누락 - docId={}", event.getApprovalDocId());
            return;
        }
        SeverancePays sev = severancePaysRepository.findById(event.getSevId())
                .orElseThrow(() -> new CustomException(ErrorCode.SEVERANCE_NOT_FOUND));
        sev.submitApproval(event.getApprovalDocId());
        log.info("[SeveranceDocCreated] sevId={}, status={}, docId={}",
                sev.getSevId(), sev.getSevStatus(), event.getApprovalDocId());

//  스냅샷 저장
        if (event.getHtmlContent() != null && !event.getHtmlContent().isBlank()) {
            snapshotRepository.save(PayrollApprovalSnapshot.builder()
                    .approvalDocId(event.getApprovalDocId())
                    .approvalType(ApprovalFormType.RETIREMENT)
                    .sevId(sev.getSevId())
                    .companyId(sev.getCompany().getCompanyId())   // SeverancePays에 company 관계가 있다고 가정
                    .htmlSnapshot(event.getHtmlContent())
                    .createdAt(LocalDateTime.now())
                    .build());
            log.info("[SeveranceDocCreated] 스냅샷 저장 - docId={}, htmlLen={}",
                    event.getApprovalDocId(), event.getHtmlContent().length());
        } else {
            log.warn("[SeveranceDocCreated] htmlContent 없음 — 스냅샷 미저장. docId={}",
                    event.getApprovalDocId());
        }
    }
}

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
import java.util.List;

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
        List<Long> sevIds = event.getSevIds();
        if (sevIds == null || sevIds.isEmpty()) {
            log.warn("[SeveranceDocCreated] 이벤트 sevIds 비어있음 - docId={}",
                    event.getApprovalDocId());
            return;
        }

        // 1. sev 일괄 조회 + 회사 격리
        List<SeverancePays> sevs = severancePaysRepository
                .findAllBySevIdInAndCompany_CompanyId(sevIds, event.getCompanyId());
        if (sevs.isEmpty()) {
            log.warn("[SeveranceDocCreated] 매칭 sev 없음 - docId={}, sevIds={}",
                    event.getApprovalDocId(), sevIds);
            return;
        }
        if (sevs.size() != sevIds.size()) {
            log.warn("[SeveranceDocCreated] 일부 sev 누락 - 요청 {} / 조회 {}, docId={}",
                    sevIds.size(), sevs.size(), event.getApprovalDocId());
        }

        // 2. 묶인 sev 모두 approvalDocId 바인딩 + IN_APPROVAL 전이
        for (SeverancePays s : sevs) {
            s.submitApproval(event.getApprovalDocId());
        }
        log.info("[Severance] docCreated 적용 - docId={}, count={}",
                event.getApprovalDocId(), sevs.size());

//  스냅샷 저장
        if (event.getHtmlContent() != null && !event.getHtmlContent().isBlank()) {
            snapshotRepository.save(PayrollApprovalSnapshot.builder()
                    .approvalDocId(event.getApprovalDocId())
                    .approvalType(ApprovalFormType.RETIREMENT)
                    .sevId(sevs.size() == 1 ? sevs.get(0).getSevId() : null)  // 다인이면 null
                    .companyId(event.getCompanyId())
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

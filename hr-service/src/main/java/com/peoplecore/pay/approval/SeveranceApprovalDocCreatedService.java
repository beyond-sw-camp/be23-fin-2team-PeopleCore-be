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

@Slf4j
@Service
public class SeveranceApprovalDocCreatedService {

    private final SeverancePaysRepository severancePaysRepository;

    @Autowired
    public SeveranceApprovalDocCreatedService(SeverancePaysRepository severancePaysRepository) {
        this.severancePaysRepository = severancePaysRepository;
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
    }

}

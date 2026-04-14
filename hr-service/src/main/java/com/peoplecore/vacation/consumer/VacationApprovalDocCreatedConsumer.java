package com.peoplecore.vacation.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.event.VacationApprovalDocCreatedEvent;
import com.peoplecore.vacation.service.VacationReqService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * 휴가 결재 문서 생성 이벤트 Consumer.
 * 토픽: vacation-approval-doc-created
 * 처리: VacationReq 의 approvalDocId bind
 */
@Component
@Slf4j
public class VacationApprovalDocCreatedConsumer {

    private final VacationReqService vacationReqService;
    private final ObjectMapper objectMapper;

    @Autowired
    public VacationApprovalDocCreatedConsumer(VacationReqService vacationReqService,
                                              ObjectMapper objectMapper) {
        this.vacationReqService = vacationReqService;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 10000, multiplier = 2))
    @KafkaListener(topics = "vacation-approval-doc-created", groupId = "hr-vacation-doc-created-consumer")
    public void consume(String message) {
        try {
            VacationApprovalDocCreatedEvent event =
                    objectMapper.readValue(message, VacationApprovalDocCreatedEvent.class);
            vacationReqService.bindApprovalDoc(
                    event.getCompanyId(), event.getVacReqId(), event.getApprovalDocId());
            log.info("[Kafka] Vacation docCreated 처리 완료 - vacReqId={}, docId={}",
                    event.getVacReqId(), event.getApprovalDocId());
        } catch (Exception e) {
            log.error("[Kafka] Vacation docCreated 처리 실패 - message={}, err={}", message, e.getMessage());
            throw new RuntimeException(e);
        }
    }
}

package com.peoplecore.pay.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.event.PayrollApprovalDocCreatedEvent;
import com.peoplecore.event.SeveranceApprovalDocCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SeveranceApprovalDocCreatedConsumer {

    private final SeveranceApprovalDocCreatedService severanceApprovalDocCreatedService;
    private final ObjectMapper objectMapper;

    @Autowired
    public SeveranceApprovalDocCreatedConsumer(SeveranceApprovalDocCreatedService severanceApprovalDocCreatedService, ObjectMapper objectMapper) {
        this.severanceApprovalDocCreatedService = severanceApprovalDocCreatedService;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 10000, multiplier = 2))
    @KafkaListener(topics = "severance-approval-doc-created",
            groupId = "hr-severance-approval-consumer")
    public void consume(String message) {
        try {
            SeveranceApprovalDocCreatedEvent event = objectMapper.readValue(
                    message, SeveranceApprovalDocCreatedEvent.class);
            severanceApprovalDocCreatedService.applyDocCreated(event);
            log.info("[Kafka] SeveranceDocCreated 처리 완료 - docId={}, docId={}",
                    event.getApprovalDocId(), event.getApprovalDocId());
        } catch (Exception e) {
            log.error("[Kafka] SeveranceDocCreated 처리 실패 - message={}, error={}",
                    message, e.getMessage());
            throw new RuntimeException(e);
        }
    }


}

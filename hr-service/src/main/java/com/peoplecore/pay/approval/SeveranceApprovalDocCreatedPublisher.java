package com.peoplecore.pay.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.event.SeveranceApprovalDocCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SeveranceApprovalDocCreatedPublisher {

    private static final String TOPIC = "severance-approval-doc-created";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public SeveranceApprovalDocCreatedPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }


    public void publish(SeveranceApprovalDocCreatedEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, message);
            log.info("[Kafka] {} 발행 - sevId={}, drafterId={}",
                    TOPIC, event.getSevId(), event.getDrafterId());
        } catch (Exception e) {
            log.error("[Kafka] {} 발행 실패 - sevId={}, error={}",
                    TOPIC, event.getSevId(), e.getMessage());
            throw new RuntimeException("Kafka 발행 실패", e);
        }
    }
}

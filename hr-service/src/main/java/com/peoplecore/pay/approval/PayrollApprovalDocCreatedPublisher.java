package com.peoplecore.pay.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.event.PayrollApprovalDocCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PayrollApprovalDocCreatedPublisher {

    private static final String TOPIC = "payroll-approval-doc-created";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public PayrollApprovalDocCreatedPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(PayrollApprovalDocCreatedEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, message);
            log.info("[Kafka] {} 발행 - payrollRunId={}, drafterId={}",
                    TOPIC, event.getPayrollRunId(), event.getDrafterId());
        } catch (Exception e) {
            log.error("[Kafka] {} 발행 실패 - payrollRunId={}, error={}",
                    TOPIC, event.getPayrollRunId(), e.getMessage());
            throw new RuntimeException("Kafka 발행 실패", e);
        }
    }
}

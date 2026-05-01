package com.peoplecore.pay.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.event.PayrollApprovalDocCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PayrollApprovalDocCreatedConsumer {

    private final PayrollApprovalDocCreatedService payrollApprovalDocCreatedService;
    private final ObjectMapper objectMapper;

    @Autowired
    public PayrollApprovalDocCreatedConsumer(PayrollApprovalDocCreatedService payrollApprovalDocCreatedService, ObjectMapper objectMapper) {
        this.payrollApprovalDocCreatedService = payrollApprovalDocCreatedService;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 10000, multiplier = 2))
    @KafkaListener(topics = "payroll-approval-doc-created",
            groupId = "hr-payroll-approval-consumer")
    public void consume(String message) {
        try {
            PayrollApprovalDocCreatedEvent event = objectMapper.readValue(
                    message, PayrollApprovalDocCreatedEvent.class);
            payrollApprovalDocCreatedService.applyDocCreated(event);
            log.info("[Kafka] PayrollDocCreated 처리 완료 - payrollRunId={}, docId={}",
                    event.getPayrollRunId(), event.getApprovalDocId());
        } catch (Exception e) {
            log.error("[Kafka] PayrollDocCreated 처리 실패 - message={}, error={}",
                    message, e.getMessage());
            throw new RuntimeException(e);
        }
    }


}

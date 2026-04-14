package com.peoplecore.pay.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.pay.dtos.PayrollApprovedEvent;
import com.peoplecore.pay.service.PayrollService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PayrollApprovalConsumer {

    private final PayrollService payrollService;
    private final ObjectMapper objectMapper;

    @Autowired
    public PayrollApprovalConsumer(PayrollService payrollService, ObjectMapper objectMapper) {
        this.payrollService = payrollService;
        this.objectMapper = objectMapper;
    }

    /* attempts: 총시도 횟수, backoff: 재시도 간격(1초), multiplier: 시도 때마다 대기시간은 이전의 2배 (1초->2초->4초...) */
    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 1000, multiplier = 2))
    @KafkaListener(topics = "payroll-approved", groupId = "pay-approval")
    public void handlerPayrollApproved(String message){
        try {
            PayrollApprovedEvent event = objectMapper.readValue(message, PayrollApprovedEvent.class);
            payrollService.approvePayroll(event.getCompanyId(), event.getPayrollRunId());
            log.info("급여대장 전자결재 승인 처리 완료: payrollRunId={}", event.getPayrollRunId());
        } catch (Exception e ){
            log.error("급여대장 전자결재 승인 처리 실패: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

}

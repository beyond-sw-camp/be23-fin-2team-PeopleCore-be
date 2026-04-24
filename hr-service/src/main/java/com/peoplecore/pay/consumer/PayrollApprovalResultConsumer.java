package com.peoplecore.pay.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.event.PayrollApprovalResultEvent;
import com.peoplecore.pay.service.PayrollService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PayrollApprovalResultConsumer {
//      전자결재 - 급여지급결의서 결과 kafka consumer
//    토픽: payroll-approval-result
//    그룹: hr-payroll-approval-consumer

    private final PayrollService payrollService;
    private final ObjectMapper objectMapper;

    @Autowired
    public PayrollApprovalResultConsumer(PayrollService payrollService, ObjectMapper objectMapper) {
        this.payrollService = payrollService;
        this.objectMapper = objectMapper;
    }

    /* attempts: 총시도 횟수, backoff: 재시도 간격(1초), multiplier: 시도 때마다 대기시간은 이전의 2배 (1초->2초->4초...) */
    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 1000, multiplier = 2))
    @KafkaListener(topics = "payroll-approval-result", groupId = "hr-payroll-approval-consumer")
    public void consume(String message){
        try {
            PayrollApprovalResultEvent event = objectMapper.readValue(message, PayrollApprovalResultEvent.class);
            payrollService.applyApprovalResult(event);
            log.info("[kafka] 급여대장 전자결재 결과 처리 완료: payrollRunId={}, status={}",  event.getPayrollRunId(), event.getStatus());
        } catch (Exception e ){
            log.error("[kafka] 급여대장 전자결재 결과 처리 실패: - message={}, error={}", message, e.getMessage());
            throw new RuntimeException(e);
        }
    }


    
}

package com.peoplecore.pay.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.event.PayrollApprovalResultEvent;
import com.peoplecore.event.SeveranceApprovalResultEvent;
import com.peoplecore.pay.service.SeveranceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SeveranceApprovalResultConsumer {
//    전자결재 - 퇴직금 결과 kafka consumer
//  토픽: severance-approval-result
//  그룹: hr-severance-approval-consumer

    private final SeveranceService severanceService;
    private final ObjectMapper objectMapper;

    @Autowired
    public SeveranceApprovalResultConsumer(SeveranceService severanceService, ObjectMapper objectMapper) {
        this.severanceService = severanceService;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 1000, multiplier = 2))
    @KafkaListener(topics = "severance-approval-result", groupId = "hr-severance-approval-consumer")
    public void consume(String message){
        try {
            SeveranceApprovalResultEvent event = objectMapper.readValue(message, SeveranceApprovalResultEvent.class);
            severanceService.applyApprovalResult(event);
            log.info("[kafka] 퇴직금대장 전자결재 결과 처리 완료: sevId={}, status={}",  event.getSevId(), event.getStatus());
        } catch (Exception e ){
            log.error("[kafka] 퇴직금대장 전자결재 결과 처리 실패: - message={}, error={}", message, e.getMessage());
            throw new RuntimeException(e);
        }
    }

}

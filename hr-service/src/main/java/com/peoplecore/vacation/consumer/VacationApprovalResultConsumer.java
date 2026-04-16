package com.peoplecore.vacation.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.event.VacationApprovalResultEvent;
import com.peoplecore.vacation.service.VacationReqService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/*휴가 결재 결과 Kafka Consumer*/
@Component
@Slf4j
public class VacationApprovalResultConsumer {

    private final VacationReqService vacationReqService;
    private final ObjectMapper objectMapper;

    @Autowired
    public VacationApprovalResultConsumer(VacationReqService vacationReqService,
                                          ObjectMapper objectMapper) {
        this.vacationReqService = vacationReqService;
        this.objectMapper = objectMapper;
    }

    /**
     * 메시지 수신 → 역직렬화 → 서비스 위임.
     */
    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 10000, multiplier = 2))
    @KafkaListener(topics = "vacation-approval-result", groupId = "hr-vacation-approval-consumer")
    public void consume(String message) {
        try {
            VacationApprovalResultEvent event =
                    objectMapper.readValue(message, VacationApprovalResultEvent.class);
            vacationReqService.applyApprovalResult(event);
            log.info("[Kafka] VacationApprovalResult 처리 완료 - vacReqId={}, status={}",
                    event.getVacReqId(), event.getStatus());
        } catch (Exception e) {
            log.error("[Kafka] VacationApprovalResult 처리 실패 - message={}, error={}",
                    message, e.getMessage());
            throw new RuntimeException(e);
        }
    }

}

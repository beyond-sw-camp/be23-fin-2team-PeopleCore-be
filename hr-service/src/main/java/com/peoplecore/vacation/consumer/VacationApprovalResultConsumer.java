package com.peoplecore.vacation.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.event.VacationApprovalResultEvent;
import com.peoplecore.vacation.service.VacationRequestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/* 휴가 결재 결과 이벤트 수신 - VacationRequest 상태 갱신 */
@Component
@Slf4j
public class VacationApprovalResultConsumer {

    private final VacationRequestService vacationRequestService;
    private final ObjectMapper objectMapper;

    @Autowired
    public VacationApprovalResultConsumer(VacationRequestService vacationRequestService,
                                          ObjectMapper objectMapper) {
        this.vacationRequestService = vacationRequestService;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 10_000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(
            topics = "vacation-approval-result",
            groupId = "hr-vacation-approval-consumer"
    )
    public void onMessage(String payload) {
        try {
            VacationApprovalResultEvent event = objectMapper.readValue(payload, VacationApprovalResultEvent.class);
            vacationRequestService.applyApprovalResult(event);
        } catch (Exception e) {
            log.error("[VacationApprovalResult] 처리 실패 - payload={}, err={}", payload, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
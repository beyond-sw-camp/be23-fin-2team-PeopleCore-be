package com.peoplecore.vacation.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.event.VacationApprovalDocCreatedEvent;
import com.peoplecore.vacation.service.VacationRequestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/* 휴가 결재 상신 이벤트 수신 - VacationRequest INSERT (PENDING) */
@Component
@Slf4j
public class VacationApprovalDocCreatedConsumer {

    private final VacationRequestService vacationRequestService;
    private final ObjectMapper objectMapper;

    @Autowired
    public VacationApprovalDocCreatedConsumer(VacationRequestService vacationRequestService,
                                              ObjectMapper objectMapper) {
        this.vacationRequestService = vacationRequestService;
        this.objectMapper = objectMapper;
    }

    /* 재시도 정책: 3회, 지수 백오프 (10s → 20s → 40s) */
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 10_000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(
            topics = "vacation-approval-doc-created",
            groupId = "hr-vacation-doc-created-consumer"
    )
    public void onMessage(String payload) {
        try {
            VacationApprovalDocCreatedEvent event = objectMapper.readValue(payload, VacationApprovalDocCreatedEvent.class);
            vacationRequestService.createFromApproval(event);
        } catch (Exception e) {
            log.error("[VacationApprovalDocCreated] 처리 실패 - payload={}, err={}", payload, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
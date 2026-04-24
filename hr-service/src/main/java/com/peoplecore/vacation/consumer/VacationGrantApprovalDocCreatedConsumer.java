package com.peoplecore.vacation.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.event.VacationGrantApprovalDocCreatedEvent;
import com.peoplecore.vacation.service.VacationGrantRequestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/* 휴가 부여 신청 결재 상신 이벤트 수신 - VacationGrantRequest INSERT (PENDING) */
/* CONCURRENT_REQUEST_LOCK_FAILED 발생 시 RetryableTopic 재시도로 자연 처리 */
@Component
@Slf4j
public class VacationGrantApprovalDocCreatedConsumer {

    private final VacationGrantRequestService vacationGrantRequestService;
    private final ObjectMapper objectMapper;

    @Autowired
    public VacationGrantApprovalDocCreatedConsumer(VacationGrantRequestService vacationGrantRequestService,
                                                   ObjectMapper objectMapper) {
        this.vacationGrantRequestService = vacationGrantRequestService;
        this.objectMapper = objectMapper;
    }

    /* 재시도 정책: 3회, 지수 백오프 (10s → 20s → 40s) */
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 10_000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(
            topics = "vacation-grant-approval-doc-created",
            groupId = "hr-vacation-grant-doc-created-consumer"
    )
    public void onMessage(String payload) {
        try {
            VacationGrantApprovalDocCreatedEvent event = objectMapper.readValue(payload, VacationGrantApprovalDocCreatedEvent.class);
            vacationGrantRequestService.createFromApproval(event);
        } catch (Exception e) {
            log.error("[VacationGrantApprovalDocCreated] 처리 실패 - payload={}, err={}", payload, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}

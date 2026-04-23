package com.peoplecore.vacation.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.event.VacationApprovalResultEvent;
import com.peoplecore.vacation.service.VacationGrantRequestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/* 휴가 부여 신청 결재 결과 이벤트 수신 - VacationGrantRequest 상태 갱신 + Balance accrue */
/* USE 와 동일한 VacationApprovalResultEvent DTO 재사용. 토픽만 분리 (vacation-grant-approval-result) */
@Component
@Slf4j
public class VacationGrantApprovalResultConsumer {

    private final VacationGrantRequestService vacationGrantRequestService;
    private final ObjectMapper objectMapper;

    @Autowired
    public VacationGrantApprovalResultConsumer(VacationGrantRequestService vacationGrantRequestService,
                                               ObjectMapper objectMapper) {
        this.vacationGrantRequestService = vacationGrantRequestService;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 10_000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(
            topics = "vacation-grant-approval-result",
            groupId = "hr-vacation-grant-result-consumer"
    )
    public void onMessage(String payload) {
        try {
            VacationApprovalResultEvent event = objectMapper.readValue(payload, VacationApprovalResultEvent.class);
            vacationGrantRequestService.applyApprovalResult(event);
        } catch (Exception e) {
            log.error("[VacationGrantApprovalResult] 처리 실패 - payload={}, err={}", payload, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}

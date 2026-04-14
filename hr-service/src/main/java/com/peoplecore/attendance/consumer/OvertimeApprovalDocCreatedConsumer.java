package com.peoplecore.attendance.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.attendance.service.OvertimeRequestService;
import com.peoplecore.event.OvertimeApprovalDocCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * 초과근무 결재 문서 생성 이벤트 Consumer.
 * 토픽: overtime-approval-doc-created
 * 처리: OvertimeRequest 의 approvalDocId bind + DRAFT → PENDING 승격
 */
@Component
@Slf4j
public class OvertimeApprovalDocCreatedConsumer {

    private final OvertimeRequestService overtimeRequestService;
    private final ObjectMapper objectMapper;

    @Autowired
    public OvertimeApprovalDocCreatedConsumer(OvertimeRequestService overtimeRequestService,
                                              ObjectMapper objectMapper) {
        this.overtimeRequestService = overtimeRequestService;
        this.objectMapper = objectMapper;
    }

    /** JSON 역직렬화 → Service 위임. 실패 시 RuntimeException 으로 래핑해 재시도 정책 발동 */
    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 10000, multiplier = 2))
    @KafkaListener(topics = "overtime-approval-doc-created", groupId = "hr-overtime-doc-created-consumer")
    public void consume(String message) {
        try {
            OvertimeApprovalDocCreatedEvent event =
                    objectMapper.readValue(message, OvertimeApprovalDocCreatedEvent.class);
            overtimeRequestService.bindApprovalDoc(
                    event.getCompanyId(), event.getOtId(), event.getApprovalDocId());
            log.info("[Kafka] OT docCreated 처리 완료 - otId={}, docId={}",
                    event.getOtId(), event.getApprovalDocId());
        } catch (Exception e) {
            log.error("[Kafka] OT docCreated 처리 실패 - message={}, err={}", message, e.getMessage());
            throw new RuntimeException(e);
        }
    }
}

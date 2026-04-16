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
 * 처리: OvertimeRequest INSERT (PENDING) + BLOCK/NOTIFY 정책 검증 후 초과 시 알림
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

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 10000, multiplier = 2))
    @KafkaListener(topics = "overtime-approval-doc-created", groupId = "hr-overtime-doc-created-consumer")
    public void consume(String message) {
        try {
            OvertimeApprovalDocCreatedEvent event =
                    objectMapper.readValue(message, OvertimeApprovalDocCreatedEvent.class);
            overtimeRequestService.createFromApproval(event);
            log.info("[Kafka] OT docCreated 처리 완료 - docId={}, empId={}",
                    event.getApprovalDocId(), event.getEmpId());
        } catch (Exception e) {
            log.error("[Kafka] OT docCreated 처리 실패 - message={}, err={}", message, e.getMessage());
            throw new RuntimeException(e);
        }
    }
}

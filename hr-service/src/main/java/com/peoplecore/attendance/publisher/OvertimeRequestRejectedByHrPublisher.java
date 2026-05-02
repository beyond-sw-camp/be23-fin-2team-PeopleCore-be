package com.peoplecore.attendance.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.event.OvertimeRequestRejectedByHrEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/*
 * hr-service → collaboration-service 역방향 이벤트 Publisher.
 * 토픽: overtime-request-rejected-by-hr
 * 발행 시점: OT 결재 통과 후 BLOCK 정책 + 주간 한도 초과 시 자동 반려.
 */
@Slf4j
@Component
public class OvertimeRequestRejectedByHrPublisher {

    private static final String TOPIC = "overtime-request-rejected-by-hr";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public OvertimeRequestRejectedByHrPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                                ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /* 이벤트 발행. 실패 시 로그만 — 보상 로직은 collab 측에서 별도 처리 */
    public void publish(OvertimeRequestRejectedByHrEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, message);
            log.info("[OvertimeRequestRejected] 발행 - docId={}", event.getApprovalDocId());
        } catch (Exception e) {
            log.error("[OvertimeRequestRejected] 발행 실패 - docId={}, err={}",
                    event.getApprovalDocId(), e.getMessage());
        }
    }
}

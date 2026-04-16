package com.peoplecore.attendance.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.event.AttendanceModifyRejectedByHrEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/*
 * hr-service → collaboration-service 역방향 이벤트 Publisher.
 * 토픽: attendance-modify-rejected-by-hr
 * 발행 시점: 동일 (empId, comRecId) 에 PENDING 정정 신청이 이미 존재하여 자동 반려 필요할 때.
 * collab 측 Consumer 가 approvalDocId 로 문서 찾아 반려 처리.
 */
@Slf4j
@Component
public class AttendanceModifyRejectedByHrPublisher {

    private static final String TOPIC = "attendance-modify-rejected-by-hr";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public AttendanceModifyRejectedByHrPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                                 ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /* 이벤트 발행. 실패 시 로그만 — 보상 로직은 collab 측에서 별도 처리 */
    public void publish(AttendanceModifyRejectedByHrEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, message);
            log.info("[AttendanceModifyRejected] 발행 - docId={}", event.getApprovalDocId());
        } catch (Exception e) {
            log.error("[AttendanceModifyRejected] 발행 실패 - docId={}, err={}",
                    event.getApprovalDocId(), e.getMessage());
        }
    }
}
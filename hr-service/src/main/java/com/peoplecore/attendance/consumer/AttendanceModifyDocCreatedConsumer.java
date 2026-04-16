 package com.peoplecore.attendance.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.attendance.service.AttendanceModifyService;
import com.peoplecore.event.AttendanceModifyDocCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/*
 * 근태 정정 결재 문서 생성 이벤트 Consumer (collab → hr).
 * 토픽: attendance-modify-doc-created
 */
@Component
@Slf4j
public class AttendanceModifyDocCreatedConsumer {

    private final AttendanceModifyService attendanceModifyService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AttendanceModifyDocCreatedConsumer(AttendanceModifyService attendanceModifyService,
                                              ObjectMapper objectMapper) {
        this.attendanceModifyService = attendanceModifyService;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 10000, multiplier = 2))
    @KafkaListener(topics = "attendance-modify-doc-created",
                   groupId = "hr-attendance-modify-doc-created-consumer")
    public void consume(String message) {
        try {
            AttendanceModifyDocCreatedEvent event =
                    objectMapper.readValue(message, AttendanceModifyDocCreatedEvent.class);
            attendanceModifyService.createFromApproval(event);
            log.info("[Kafka] AttendanceModify docCreated 처리 완료 - docId={}",
                    event.getApprovalDocId());
        } catch (Exception e) {
            log.error("[Kafka] AttendanceModify docCreated 처리 실패 - message={}, err={}",
                    message, e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
package com.peoplecore.attendance.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.attendance.service.AttendanceModifyService;
import com.peoplecore.event.AttendanceModifyResultEvent;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/*
 * 근태 정정 결재 결과 이벤트 Consumer (collab → hr).
 * 토픽: attendance-modify-result
 */
@Component
@Slf4j
public class AttendanceModifyResultConsumer {

    private final AttendanceModifyService attendanceModifyService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AttendanceModifyResultConsumer(AttendanceModifyService attendanceModifyService,
                                          ObjectMapper objectMapper) {
        this.attendanceModifyService = attendanceModifyService;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 10000, multiplier = 2))
    @KafkaListener(topics = "attendance-modify-result",
            groupId = "hr-attendance-modify-result-consumer")
    public void consume(String message) {
        try {
            AttendanceModifyResultEvent event =
                    objectMapper.readValue(message, AttendanceModifyResultEvent.class);
            attendanceModifyService.applyApprovalResult(event);
            log.info("[Kafka] AttendanceModify result 처리 완료 - docId={}, status={}",
                    event.getApprovalDocId(), event.getStatus());
        } catch (CustomException ce) {
            // 멱등 처리: 이미 전이된 상태면 INVALID_STATUS_TRANSITION → swallow
            if (ce.getErrorCode() == ErrorCode.INVALID_STATUS_TRANSITION) {
                log.info("[Kafka] AttendanceModify result 중복 수신 — 이미 처리됨. message={}", message);
                return;
            }
            // 그 외 CustomException 은 재시도
            log.error("[Kafka] AttendanceModify result 처리 실패 - err={}, message={}",
                    ce.getErrorCode(), message);
            throw ce;
        } catch (Exception e) {
            log.error("[Kafka] AttendanceModify result 처리 실패 - message={}, err={}",
                    message, e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
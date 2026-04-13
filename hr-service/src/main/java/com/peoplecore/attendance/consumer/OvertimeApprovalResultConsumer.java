package com.peoplecore.attendance.consumer;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.attendance.service.OvertimeRequestService;
import com.peoplecore.event.OvertimeApprovalResultEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/*초과 근무 결재 결과 kafka Consumer
 * 토픽은 overtime-approval-result
 * 그룹은 hr-overtime-approval-consumer
 * */
@Component
@Slf4j
public class OvertimeApprovalResultConsumer {
    private final OvertimeRequestService overtimeRequestService;
    private final ObjectMapper objectMapper;

    public OvertimeApprovalResultConsumer(OvertimeRequestService overtimeRequestService, ObjectMapper objectMapper) {
        this.overtimeRequestService = overtimeRequestService;
        this.objectMapper = objectMapper;
    }

    /*메시지 수신 -> 역직렬화 -> 서비스 위임 */
    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 10000, multiplier = 2))
    @KafkaListener(topics = "overtime-approval-result", groupId = "hr-overtime-approval-consumer")
    public void consume(String message) {
        try {
            OvertimeApprovalResultEvent event = objectMapper.readValue(message, OvertimeApprovalResultEvent.class);
            overtimeRequestService.applyApprovalResult(event);
            log.info("[Kafka] OvertimeApprovalResult 처리 완료 - otId={}, status={}",
                    event.getOtId(), event.getStatus());
        } catch (Exception e) {
            log.error("[Kafka] OvertimeApprovalResult 처리 실패 - message={}, error={}",
                    message, e.getMessage());
            throw new RuntimeException(e);        }

    }

}

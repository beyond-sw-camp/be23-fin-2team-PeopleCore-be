package com.peoplecore.alarm.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.event.AlarmEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * hr-service → alarm-event 토픽 발행.
 * collab 의 AlarmEventConsumer 가 수신해서 common_alarm 테이블 저장 + 실시간 알림 발송.
 * collab 의 AlarmEventPublisher 와 동일 패턴.
 */
@Component
@Slf4j
public class HrAlarmPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public HrAlarmPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /** AlarmEvent 를 alarm-event 토픽으로 발행. 실패해도 예외 전파 안 함 (로그만) */
    public void publisher(AlarmEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("alarm-event", message);
        } catch (Exception e) {
            log.error("[HrAlarm] 알림 이벤트 발행 실패 - err={}", e.getMessage());
        }
    }
}

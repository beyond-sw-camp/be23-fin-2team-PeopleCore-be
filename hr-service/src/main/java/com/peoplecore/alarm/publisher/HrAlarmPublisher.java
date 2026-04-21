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


    /*
     * 예외 전파 버전 - 법적 효력 있는 알림(연차 촉진 통지 등)에서 실패 감지용.
     * kafkaTemplate.send().get() 동기 대기 → 실패 시 RuntimeException 래핑해 throw.
     * 호출부의 @Retryable 이 예외를 받아 재시도 트리거.
     */
    public void publisherOrThrow(AlarmEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("alarm-event", message).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("알림 이벤트 발행 인터럽트", e);
        } catch (Exception e) {
            log.error("[HrAlarm] 알림 발행 실패(throw) - err={}", e.getMessage());
            throw new RuntimeException("알림 이벤트 발행 실패", e);
        }
    }
}

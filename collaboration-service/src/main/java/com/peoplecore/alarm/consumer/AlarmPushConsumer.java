package com.peoplecore.alarm.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.alarm.service.AlarmService;
import com.peoplecore.event.AlarmEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/* alarm-event 토픽의 SSE push 책임자. groupId 에 HOSTNAME 을 포함해 pod 마다 다른 group
   → Kafka 가 broadcast(fanout) 처럼 동작하여 모든 collaboration-service pod 이 메시지를 받음.
   각 pod 은 자기 emitterMap 에 있는 사원에게만 send, 없는 사원은 자연스럽게 drop */
@Component
@Slf4j
public class AlarmPushConsumer {

    private final AlarmService alarmService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AlarmPushConsumer(AlarmService alarmService, ObjectMapper objectMapper) {
        this.alarmService = alarmService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "alarm-event",
            groupId = "collaboration-alarm-push-${HOSTNAME:local}",
            properties = "auto.offset.reset=latest"
    )
    public void consume(String message) {
        try {
            AlarmEvent event = objectMapper.readValue(message, AlarmEvent.class);
            alarmService.push(event);
        } catch (Exception e) {
            // 끊긴 SSE 에 send 시도하면 AsyncRequestNotUsableException 등이 빈번히 발생 → warn 으로 노이즈 축소
            log.warn("알림 SSE push 실패 ({}): {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }
}

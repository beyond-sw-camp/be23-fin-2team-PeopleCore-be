package com.peoplecore.alarm.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.alarm.service.AlarmService;
import com.peoplecore.event.AlarmEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/* alarm-event 토픽의 DB 저장 책임자. 정적 group-id 라 모든 pod 이 같은 group 에 묶여
   메시지 1건당 1 pod 만 처리 → 중복 insert 방지 */
@Component
@Slf4j
public class AlarmPersistConsumer {

    private final AlarmService alarmService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AlarmPersistConsumer(AlarmService alarmService, ObjectMapper objectMapper) {
        this.alarmService = alarmService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "alarm-event", groupId = "collaboration-alarm-persist")
    public void consume(String message) {
        try {
            AlarmEvent event = objectMapper.readValue(message, AlarmEvent.class);
            alarmService.persist(event);
            log.info("알림 DB 저장 완료 type={}, count={}", event.getAlarmType(), event.getEmpIds().size());
        } catch (Exception e) {
            log.error("알림 DB 저장 실패 : {} ", e.getMessage());
        }
    }
}

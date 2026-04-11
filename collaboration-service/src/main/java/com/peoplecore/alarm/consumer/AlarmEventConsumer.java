package com.peoplecore.alarm.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.alarm.service.AlarmService;
import com.peoplecore.event.AlarmEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AlarmEventConsumer {

    private final AlarmService alarmService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AlarmEventConsumer(AlarmService alarmService, ObjectMapper objectMapper) {
        this.alarmService = alarmService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "alarm-event", groupId = "collaboration-alarm")
    public void consume(String message) {
        try {
            AlarmEvent event = objectMapper.readValue(message, AlarmEvent.class);
            alarmService.createAndPush(event);
            log.info("알림 저장 완료 type = {} ", event.getAlarmType());
        } catch (Exception e) {
            log.error("알림 이벤트 처리 실패 : {} ", e.getMessage());
        }
    }
}

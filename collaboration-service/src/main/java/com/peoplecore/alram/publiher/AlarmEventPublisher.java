package com.peoplecore.alram.publiher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.event.AlarmEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AlarmEventPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public AlarmEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publisher(AlarmEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("alarm-event", message);
        } catch (Exception e) {
            log.error("알림 이벤트 발행 실패 : {}", e.getMessage());
        }
    }
}

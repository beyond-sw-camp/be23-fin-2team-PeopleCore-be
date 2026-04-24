package com.peoplecore.resign.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.event.ResignApprovedEvent;
import com.peoplecore.resign.service.ResignService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ResignApprovedConsumer {

    private final ResignService resignService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ResignApprovedConsumer(ResignService resignService, ObjectMapper objectMapper) {
        this.resignService = resignService;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 10000, multiplier = 2))
    @KafkaListener(topics = "resign-approved", groupId = "hr-resign-consumer")
    public void consume(String message) {
        try {
            ResignApprovedEvent event = objectMapper.readValue(message, ResignApprovedEvent.class);
            resignService.createResignFromApprocal(event);
            log.info("퇴직 신청 생성완료");
        } catch (Exception e) {
            log.error("퇴직 승인 이벤트 처리 실패");
            throw new RuntimeException(e);
        }
    }
}

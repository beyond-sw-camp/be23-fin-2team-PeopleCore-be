package com.peoplecore.chat.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.chat.dto.ChatMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatKafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String TOPIC_CHAT_MESSAGES = "chat-messages";

    public void sendMessageEvent(ChatMessageEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC_CHAT_MESSAGES, String.valueOf(event.getRoomId()), message);
            log.info("채팅 메시지 이벤트 발행 roomId={}, sender={}", event.getRoomId(), event.getSenderEmpId());
        } catch (JsonProcessingException e) {
            log.error("채팅 메시지 직렬화 실패 roomId={}, error={}", event.getRoomId(), e.getMessage());
        }
    }
}

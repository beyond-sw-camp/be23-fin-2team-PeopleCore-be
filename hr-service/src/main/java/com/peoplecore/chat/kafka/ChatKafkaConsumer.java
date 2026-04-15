package com.peoplecore.chat.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.chat.domain.ChatParticipant;
import com.peoplecore.chat.dto.ChatMessageEvent;
import com.peoplecore.chat.repository.ChatParticipantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
public class ChatKafkaConsumer {

    private final ChatParticipantRepository chatParticipantRepository;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> chatRedisTemplate;

    public ChatKafkaConsumer(
            ChatParticipantRepository chatParticipantRepository,
            ObjectMapper objectMapper,
            @Qualifier("chatRedisTemplate") RedisTemplate<String, Object> chatRedisTemplate) {
        this.chatParticipantRepository = chatParticipantRepository;
        this.objectMapper = objectMapper;
        this.chatRedisTemplate = chatRedisTemplate;
    }

    @KafkaListener(topics = "chat-messages", groupId = "chat-message-consumer")
    @Transactional
    public void consumeMessage(String message) {
        try {
            ChatMessageEvent event = objectMapper.readValue(message, ChatMessageEvent.class);

            // DB 저장은 sendMessage에서 동기 처리됨. 여기서는 Redis unread만 갱신.
            List<ChatParticipant> participants = chatParticipantRepository
                    .findByChatRoom_RoomIdAndIsActiveTrue(event.getRoomId());

            for (ChatParticipant p : participants) {
                if (!p.getEmployee().getEmpId().equals(event.getSenderEmpId())) {
                    String key = "unread:user:" + p.getEmployee().getEmpId() + ":room:" + event.getRoomId();
                    chatRedisTemplate.opsForValue().increment(key);

                    String totalKey = "unread:user:" + p.getEmployee().getEmpId() + ":total";
                    chatRedisTemplate.opsForValue().increment(totalKey);
                }
            }

            log.info("채팅 unread 갱신 완료 roomId={}", event.getRoomId());

        } catch (JsonProcessingException e) {
            log.error("채팅 메시지 역직렬화 실패: {}", e.getMessage());
        }
    }
}

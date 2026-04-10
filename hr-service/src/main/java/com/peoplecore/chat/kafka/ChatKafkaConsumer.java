package com.peoplecore.chat.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.chat.domain.ChatMessage;
import com.peoplecore.chat.domain.ChatParticipant;
import com.peoplecore.chat.domain.ChatRoom;
import com.peoplecore.chat.domain.MessageType;
import com.peoplecore.chat.dto.ChatMessageEvent;
import com.peoplecore.chat.repository.ChatMessageRepository;
import com.peoplecore.chat.repository.ChatParticipantRepository;
import com.peoplecore.chat.repository.ChatRoomRepository;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
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

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final EmployeeRepository employeeRepository;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> chatRedisTemplate;

    public ChatKafkaConsumer(
            ChatMessageRepository chatMessageRepository,
            ChatRoomRepository chatRoomRepository,
            ChatParticipantRepository chatParticipantRepository,
            EmployeeRepository employeeRepository,
            ObjectMapper objectMapper,
            @Qualifier("chatRedisTemplate") RedisTemplate<String, Object> chatRedisTemplate) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.chatParticipantRepository = chatParticipantRepository;
        this.employeeRepository = employeeRepository;
        this.objectMapper = objectMapper;
        this.chatRedisTemplate = chatRedisTemplate;
    }

    @KafkaListener(topics = "chat-messages", groupId = "chat-message-consumer")
    @Transactional
    public void consumeMessage(String message) {
        try {
            ChatMessageEvent event = objectMapper.readValue(message, ChatMessageEvent.class);

            ChatRoom chatRoom = chatRoomRepository.findById(event.getRoomId())
                    .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다: " + event.getRoomId()));

            Employee sender = employeeRepository.findById(event.getSenderEmpId())
                    .orElseThrow(() -> new IllegalArgumentException("사원을 찾을 수 없습니다: " + event.getSenderEmpId()));

            // 1. DB 저장
            ChatMessage chatMessage = ChatMessage.builder()
                    .chatRoom(chatRoom)
                    .sender(sender)
                    .msgContent(event.getContent())
                    .msgType(event.getMsgType() != null ? MessageType.valueOf(event.getMsgType()) : MessageType.TEXT)
                    .fileUrl(event.getFileUrl())
                    .fileName(event.getFileName())
                    .fileSize(event.getFileSize())
                    .build();
            chatMessageRepository.save(chatMessage);

            // 2. 채팅방 마지막 메시지 시간 갱신
            chatRoom.updateLastMessageAt(chatMessage.getCreatedAt());

            // 3. Redis unread 갱신
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

            log.info("채팅 메시지 저장 완료 msgId={}, roomId={}", chatMessage.getMsgId(), event.getRoomId());

        } catch (JsonProcessingException e) {
            log.error("채팅 메시지 역직렬화 실패: {}", e.getMessage());
        }
    }
}

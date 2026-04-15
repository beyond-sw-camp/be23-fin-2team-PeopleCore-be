package com.peoplecore.chat.service;

import com.peoplecore.chat.domain.ChatMessage;
import com.peoplecore.chat.domain.ChatParticipant;
import com.peoplecore.chat.domain.ChatRoom;
import com.peoplecore.chat.domain.MessageType;
import com.peoplecore.chat.dto.ChatMessageEvent;
import com.peoplecore.chat.dto.ChatMessageRequest;
import com.peoplecore.chat.dto.ChatMessageResponse;
import com.peoplecore.chat.dto.ChatReadEvent;
import com.peoplecore.chat.kafka.ChatKafkaProducer;
import com.peoplecore.chat.repository.ChatMessageRepository;
import com.peoplecore.chat.repository.ChatParticipantRepository;
import com.peoplecore.chat.repository.ChatRoomRepository;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final EmployeeRepository employeeRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatKafkaProducer chatKafkaProducer;
    private final RedisTemplate<String, Object> chatRedisTemplate;

    public ChatMessageService(
            ChatMessageRepository chatMessageRepository,
            ChatRoomRepository chatRoomRepository,
            ChatParticipantRepository chatParticipantRepository,
            EmployeeRepository employeeRepository,
            SimpMessagingTemplate messagingTemplate,
            ChatKafkaProducer chatKafkaProducer,
            @Qualifier("chatRedisTemplate") RedisTemplate<String, Object> chatRedisTemplate) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.chatParticipantRepository = chatParticipantRepository;
        this.employeeRepository = employeeRepository;
        this.messagingTemplate = messagingTemplate;
        this.chatKafkaProducer = chatKafkaProducer;
        this.chatRedisTemplate = chatRedisTemplate;
    }

    @Transactional
    public void sendMessage(Long senderEmpId, ChatMessageRequest request) {
        Employee sender = employeeRepository.findById(senderEmpId)
                .orElseThrow(() -> new IllegalArgumentException("사원을 찾을 수 없습니다."));

        ChatRoom chatRoom = chatRoomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        String msgType = request.getMsgType() != null ? request.getMsgType() : "TEXT";
        String displayContent = request.getContent();
        if (("FILE".equals(msgType) || "IMAGE".equals(msgType)) && request.getFileName() != null) {
            displayContent = request.getFileName();
        }

        // 1. DB 저장 (동기) — 실 msgId 확보
        ChatMessage chatMessage = ChatMessage.builder()
                .chatRoom(chatRoom)
                .sender(sender)
                .msgContent(displayContent)
                .msgType(MessageType.valueOf(msgType))
                .fileUrl(request.getFileUrl())
                .fileName(request.getFileName())
                .fileSize(request.getFileSize())
                .build();
        chatMessageRepository.save(chatMessage);
        chatRoom.updateLastMessageAt(chatMessage.getCreatedAt());

        // 2. STOMP 브로드캐스트 (실 msgId 사용)
        ChatMessageResponse response = ChatMessageResponse.builder()
                .msgId(chatMessage.getMsgId())
                .roomId(request.getRoomId())
                .senderId(senderEmpId)
                .senderName(sender.getEmpName())
                .senderProfileImageUrl(sender.getEmpProfileImageUrl())
                .content(displayContent)
                .msgType(msgType)
                .fileUrl(request.getFileUrl())
                .fileName(request.getFileName())
                .fileSize(request.getFileSize())
                .createdAt(chatMessage.getCreatedAt())
                .build();
        messagingTemplate.convertAndSend("/sub/chat/room/" + request.getRoomId(), response);

        // 2. 참여자에게 STOMP unread 알림 즉시 발행
        List<ChatParticipant> participants = chatParticipantRepository
                .findByChatRoom_RoomIdAndIsActiveTrue(request.getRoomId());

        for (ChatParticipant p : participants) {
            if (!p.getEmployee().getEmpId().equals(senderEmpId)) {
                Long targetEmpId = p.getEmployee().getEmpId();
                // 음소거된 사용자에게는 unread 알림 전송하지 않음
                if (p.getIsMuted() != null && p.getIsMuted()) continue;
                messagingTemplate.convertAndSend(
                        "/sub/user/" + targetEmpId + "/unread",
                        java.util.Map.of(
                                "roomId", request.getRoomId(),
                                "senderName", sender.getEmpName(),
                                "content", displayContent != null ? displayContent : ""
                        )
                );
            }
        }

        // 3. Kafka로 비동기 처리 위임 (DB 저장 + Redis unread 갱신)
        ChatMessageEvent event = ChatMessageEvent.builder()
                .roomId(request.getRoomId())
                .senderEmpId(senderEmpId)
                .content(displayContent)
                .msgType(msgType)
                .fileUrl(request.getFileUrl())
                .fileName(request.getFileName())
                .fileSize(request.getFileSize())
                .build();
        chatKafkaProducer.sendMessageEvent(event);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(Long roomId, Long beforeMsgId, int size) {
        List<ChatMessage> messages;
        PageRequest pageable = PageRequest.of(0, size);

        if (beforeMsgId == null) {
            messages = chatMessageRepository.findRecentMessages(roomId, pageable);
        } else {
            messages = chatMessageRepository.findMessagesBefore(roomId, beforeMsgId, pageable);
        }

        List<ChatMessageResponse> responses = messages.stream()
                .map(this::toResponse)
                .toList();

        // DESC로 조회했으므로 ASC로 뒤집기
        List<ChatMessageResponse> result = new java.util.ArrayList<>(responses);
        Collections.reverse(result);
        return result;
    }

    @Transactional
    public void markAsRead(Long empId, Long roomId) {
        ChatParticipant participant = chatParticipantRepository
                .findByChatRoom_RoomIdAndEmployee_EmpIdAndIsActiveTrue(roomId, empId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 참여자가 아닙니다."));

        ChatMessage lastMessage = chatMessageRepository
                .findTopByChatRoom_RoomIdAndIsDeletedFalseOrderByMsgIdDesc(roomId)
                .orElse(null);

        if (lastMessage == null) return;

        participant.updateLastReadMsgId(lastMessage.getMsgId());

        // Redis unread 초기화
        String key = "unread:user:" + empId + ":room:" + roomId;
        Object unreadObj = chatRedisTemplate.opsForValue().get(key);
        if (unreadObj != null) {
            long unread = Long.parseLong(unreadObj.toString());
            chatRedisTemplate.delete(key);

            String totalKey = "unread:user:" + empId + ":total";
            chatRedisTemplate.opsForValue().decrement(totalKey, unread);
        }

        // 읽음 이벤트 브로드캐스트
        ChatReadEvent readEvent = ChatReadEvent.builder()
                .roomId(roomId)
                .empId(empId)
                .lastReadMsgId(lastMessage.getMsgId())
                .build();
        messagingTemplate.convertAndSend("/sub/chat/room/" + roomId + "/read", readEvent);
    }

    public int getTotalUnreadCount(Long empId) {
        String totalKey = "unread:user:" + empId + ":total";
        Object count = chatRedisTemplate.opsForValue().get(totalKey);
        if (count == null) return 0;
        return Integer.parseInt(count.toString());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> searchMessages(Long roomId, String keyword, int size) {
        PageRequest pageable = PageRequest.of(0, size);
        List<ChatMessage> messages = chatMessageRepository.searchMessages(roomId, keyword, pageable);
        List<ChatMessageResponse> result = new java.util.ArrayList<>(messages.stream().map(this::toResponse).toList());
        Collections.reverse(result);
        return result;
    }

    @Transactional
    public void deleteMessage(Long msgId, Long empId) {
        ChatMessage message = chatMessageRepository.findById(msgId)
                .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));

        if (!message.getSender().getEmpId().equals(empId)) {
            throw new IllegalArgumentException("본인의 메시지만 삭제할 수 있습니다.");
        }

        Long roomId = message.getChatRoom().getRoomId();
        message.softDelete();

        // 삭제 이벤트 브로드캐스트
        messagingTemplate.convertAndSend("/sub/chat/room/" + roomId + "/delete",
                java.util.Map.of("msgId", msgId));
    }

    private ChatMessageResponse toResponse(ChatMessage message) {
        return ChatMessageResponse.builder()
                .msgId(message.getMsgId())
                .roomId(message.getChatRoom().getRoomId())
                .senderId(message.getSender().getEmpId())
                .senderName(message.getSender().getEmpName())
                .senderProfileImageUrl(message.getSender().getEmpProfileImageUrl())
                .content(message.getMsgContent())
                .msgType(message.getMsgType().name())
                .fileUrl(message.getFileUrl())
                .fileName(message.getFileName())
                .fileSize(message.getFileSize())
                .createdAt(message.getCreatedAt())
                .build();
    }
}

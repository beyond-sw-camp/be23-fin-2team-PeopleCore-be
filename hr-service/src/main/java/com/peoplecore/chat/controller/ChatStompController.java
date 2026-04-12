package com.peoplecore.chat.controller;

import com.peoplecore.chat.dto.ChatMessageRequest;
import com.peoplecore.chat.service.ChatMessageService;
import com.peoplecore.chat.service.ChatRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatMessageService chatMessageService;
    private final ChatRoomService chatRoomService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat/message")
    public void handleMessage(ChatMessageRequest request, StompHeaderAccessor accessor) {
        try {
            Long empId = (Long) accessor.getSessionAttributes().get("empId");

            if (empId == null) {
                log.error("[STOMP] empId가 null입니다. 인증 인터셉터를 확인하세요.");
                return;
            }

            try {
                chatRoomService.validateParticipant(request.getRoomId(), empId);
            } catch (IllegalArgumentException e) {
                log.warn("[STOMP] 비참여자 메시지 전송 시도 empId={}, roomId={}", empId, request.getRoomId());
                return;
            }

            chatMessageService.sendMessage(empId, request);
        } catch (Exception e) {
            log.error("[STOMP] 메시지 처리 실패: {}", e.getMessage(), e);
        }
    }

    @MessageMapping("/chat/typing")
    public void handleTyping(Map<String, Object> payload, StompHeaderAccessor accessor) {
        Long empId = (Long) accessor.getSessionAttributes().get("empId");
        String empName = (String) accessor.getSessionAttributes().get("empName");
        if (empId == null) return;

        Object roomIdObj = payload.get("roomId");
        if (roomIdObj == null) return;
        Long roomId = Long.valueOf(roomIdObj.toString());

        messagingTemplate.convertAndSend("/sub/chat/room/" + roomId + "/typing",
                Map.of("empId", empId, "empName", empName != null ? empName : ""));
    }
}

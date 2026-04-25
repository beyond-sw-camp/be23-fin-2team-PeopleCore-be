package com.peoplecore.chat.config;

import com.peoplecore.auth.jwt.JwtProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtProvider jwtProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("Authorization");
            log.info("[STOMP AUTH] CONNECT 요청 수신, Authorization 헤더 존재: {}", token != null);

            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            if (token == null || !jwtProvider.validateAccessToken(token)) {
                log.error("[STOMP AUTH] JWT 검증 실패");
                throw new IllegalArgumentException("Invalid or missing JWT token");
            }

            Claims claims = jwtProvider.parseAccessToken(token);
            Long empId = Long.parseLong(claims.getSubject());
            String empName = claims.get("jobTypeName", String.class);

            accessor.getSessionAttributes().put("empId", empId);
            accessor.getSessionAttributes().put("empName", empName);
            log.info("[STOMP AUTH] 인증 성공 empId={}, empName={}", empId, empName);
        }

        return message;
    }
}

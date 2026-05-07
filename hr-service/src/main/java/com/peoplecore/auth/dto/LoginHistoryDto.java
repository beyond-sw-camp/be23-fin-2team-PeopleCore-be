package com.peoplecore.auth.dto;

import com.peoplecore.auth.domain.LoginHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class LoginHistoryDto {
    private Long id;
    private String ip;
    private String userAgent;
    private String loginMethod;
    private LocalDateTime loginAt;

    public static LoginHistoryDto from(LoginHistory h) {
        return LoginHistoryDto.builder()
                .id(h.getId())
                .ip(h.getIp())
                .userAgent(h.getUserAgent())
                .loginMethod(h.getLoginMethod())
                .loginAt(h.getLoginAt())
                .build();
    }
}

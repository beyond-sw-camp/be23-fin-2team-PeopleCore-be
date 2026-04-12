package com.peoplecore.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatMessageResponse {
    private Long msgId;
    private Long roomId;
    private Long senderId;
    private String senderName;
    private String senderProfileImageUrl;
    private String content;
    private String msgType;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private LocalDateTime createdAt;
}

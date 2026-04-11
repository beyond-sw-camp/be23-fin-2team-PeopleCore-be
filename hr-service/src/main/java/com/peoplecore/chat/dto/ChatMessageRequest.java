package com.peoplecore.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatMessageRequest {
    private Long roomId;
    private String content;
    private String msgType;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
}

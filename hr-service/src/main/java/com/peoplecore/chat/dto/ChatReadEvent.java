package com.peoplecore.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatReadEvent {
    private Long roomId;
    private Long empId;
    private Long lastReadMsgId;
}

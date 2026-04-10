package com.peoplecore.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatRoomResponse {
    private Long roomId;
    private String roomType;
    private String roomName;
    private Long createdByEmpId;
    private LocalDateTime lastMessageAt;
    private String lastMessage;
    private int unreadCount;
    private boolean muted;
    private List<ParticipantInfo> participants;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ParticipantInfo {
        private Long empId;
        private String empName;
        private String gradeName;
        private String deptName;
        private String profileImageUrl;
    }
}

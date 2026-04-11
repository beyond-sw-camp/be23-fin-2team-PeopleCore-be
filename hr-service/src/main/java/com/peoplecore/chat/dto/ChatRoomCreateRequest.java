package com.peoplecore.chat.dto;

import com.peoplecore.chat.domain.RoomType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatRoomCreateRequest {
    private RoomType roomType;
    private String roomName;
    private List<Long> memberEmpIds;
}

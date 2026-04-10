package com.peoplecore.chat.controller;

import com.peoplecore.chat.dto.ChatInviteRequest;
import com.peoplecore.chat.dto.ChatRoomCreateRequest;
import com.peoplecore.chat.dto.ChatRoomRenameRequest;
import com.peoplecore.chat.dto.ChatRoomResponse;
import com.peoplecore.chat.service.ChatFileService;
import com.peoplecore.chat.service.ChatMessageService;
import com.peoplecore.chat.service.ChatRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/chat/rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final ChatFileService chatFileService;
    private final ChatMessageService chatMessageService;

    @GetMapping
    public ResponseEntity<List<ChatRoomResponse>> getMyRooms(
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(chatRoomService.getMyRooms(empId));
    }

    @PostMapping
    public ResponseEntity<ChatRoomResponse> createRoom(
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody ChatRoomCreateRequest request) {
        return ResponseEntity.ok(chatRoomService.createRoom(empId, request));
    }

    @GetMapping("/dm")
    public ResponseEntity<ChatRoomResponse> findDmRoom(
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam("targetEmpId") Long targetEmpId) {
        if (targetEmpId == null) {
            return ResponseEntity.badRequest().build();
        }
        ChatRoomResponse room = chatRoomService.findDmRoom(empId, targetEmpId);
        if (room == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(room);
    }

    @PostMapping("/{roomId}/invite")
    public ResponseEntity<ChatRoomResponse> inviteMembers(
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long roomId,
            @RequestBody ChatInviteRequest request) {
        chatRoomService.validateParticipant(roomId, empId);
        return ResponseEntity.ok(chatRoomService.inviteMembers(roomId, empId, request.getMemberEmpIds()));
    }

    @PutMapping("/{roomId}/rename")
    public ResponseEntity<Void> renameRoom(
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long roomId,
            @RequestBody ChatRoomRenameRequest request) {
        chatRoomService.validateParticipant(roomId, empId);
        chatRoomService.renameRoom(roomId, empId, request.getRoomName());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{roomId}/mute")
    public ResponseEntity<Map<String, Boolean>> toggleMute(
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long roomId) {
        boolean muted = chatRoomService.toggleMute(roomId, empId);
        return ResponseEntity.ok(Map.of("muted", muted));
    }

    @DeleteMapping("/{roomId}/leave")
    public ResponseEntity<Void> leaveRoom(
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long roomId) {
        chatRoomService.leaveRoom(roomId, empId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{roomId}/read")
    public ResponseEntity<Void> markAsRead(
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long roomId) {
        chatMessageService.markAsRead(empId, roomId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{roomId}/messages")
    public ResponseEntity<List<?>> getMessages(
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long roomId,
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "50") int size) {
        chatRoomService.validateParticipant(roomId, empId);
        return ResponseEntity.ok(chatMessageService.getMessages(roomId, before, size));
    }

    @GetMapping("/{roomId}/messages/search")
    public ResponseEntity<List<?>> searchMessages(
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long roomId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "30") int size) {
        chatRoomService.validateParticipant(roomId, empId);
        return ResponseEntity.ok(chatMessageService.searchMessages(roomId, keyword, size));
    }

    @DeleteMapping("/messages/{msgId}")
    public ResponseEntity<Void> deleteMessage(
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long msgId) {
        chatMessageService.deleteMessage(msgId, empId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{roomId}/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long roomId,
            @RequestPart("file") MultipartFile file) {
        chatRoomService.validateParticipant(roomId, empId);
        try {
            ChatFileService.ChatFileResult result = chatFileService.uploadFile(file, roomId);
            return ResponseEntity.ok(Map.of(
                    "fileUrl", result.fileUrl(),
                    "fileName", result.fileName(),
                    "fileSize", result.fileSize(),
                    "msgType", result.isImage() ? "IMAGE" : "FILE"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "파일 업로드 실패"));
        }
    }

    @GetMapping("/unread/total")
    public ResponseEntity<Integer> getTotalUnread(
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(chatMessageService.getTotalUnreadCount(empId));
    }
}

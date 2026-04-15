package com.peoplecore.chat.service;

import com.peoplecore.chat.domain.ChatMessage;
import com.peoplecore.chat.domain.ChatParticipant;
import com.peoplecore.chat.domain.ChatRoom;
import com.peoplecore.chat.domain.RoomType;
import com.peoplecore.chat.dto.ChatRoomCreateRequest;
import com.peoplecore.chat.dto.ChatRoomResponse;
import com.peoplecore.chat.repository.ChatMessageRepository;
import com.peoplecore.chat.repository.ChatParticipantRepository;
import com.peoplecore.chat.repository.ChatRoomRepository;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final EmployeeRepository employeeRepository;
    private final RedisTemplate<String, Object> chatRedisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatRoomService(
            ChatRoomRepository chatRoomRepository,
            ChatParticipantRepository chatParticipantRepository,
            ChatMessageRepository chatMessageRepository,
            EmployeeRepository employeeRepository,
            @Qualifier("chatRedisTemplate") RedisTemplate<String, Object> chatRedisTemplate,
            SimpMessagingTemplate messagingTemplate) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatParticipantRepository = chatParticipantRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.employeeRepository = employeeRepository;
        this.chatRedisTemplate = chatRedisTemplate;
        this.messagingTemplate = messagingTemplate;
    }

    public List<ChatRoomResponse> getMyRooms(Long empId) {
        // 1회 쿼리로 방 + 참여자 + 사원 + 부서 + 직급 전부 로드
        List<ChatRoom> rooms = chatRoomRepository.findMyRoomsWithParticipants(empId);

        // 마지막 메시지를 한 번에 조회할 roomId 목록
        List<Long> roomIds = rooms.stream().map(ChatRoom::getRoomId).toList();

        // 마지막 메시지 일괄 조회 (room당 1건)
        Map<Long, String> lastMessages = new java.util.HashMap<>();
        for (Long roomId : roomIds) {
            chatMessageRepository.findTopByChatRoom_RoomIdAndIsDeletedFalseOrderByMsgIdDesc(roomId)
                    .ifPresent(msg -> lastMessages.put(roomId, msg.getMsgContent()));
        }

        // Redis unread 일괄 조회
        List<String> redisKeys = roomIds.stream()
                .map(id -> "unread:user:" + empId + ":room:" + id)
                .toList();
        List<Object> redisValues = chatRedisTemplate.opsForValue().multiGet(redisKeys);

        List<ChatRoomResponse> responses = new ArrayList<>();

        for (int i = 0; i < rooms.size(); i++) {
            ChatRoom room = rooms.get(i);

            List<ChatParticipant> activeParticipants = room.getParticipants().stream()
                    .filter(ChatParticipant::getIsActive)
                    .toList();

            ChatParticipant myParticipant = activeParticipants.stream()
                    .filter(p -> p.getEmployee().getEmpId().equals(empId))
                    .findFirst()
                    .orElse(null);
            if (myParticipant == null) continue;

            // Redis 우선, DB fallback
            int unreadCount = 0;
            Object redisUnread = redisValues != null ? redisValues.get(i) : null;
            if (redisUnread != null) {
                unreadCount = Integer.parseInt(redisUnread.toString());
            } else if (myParticipant.getLastReadMsgId() != null) {
                unreadCount = chatMessageRepository.countUnread(room.getRoomId(), myParticipant.getLastReadMsgId());
            }

            List<ChatRoomResponse.ParticipantInfo> participantInfos = activeParticipants.stream()
                    .map(p -> ChatRoomResponse.ParticipantInfo.builder()
                            .empId(p.getEmployee().getEmpId())
                            .empName(p.getEmployee().getEmpName())
                            .gradeName(p.getEmployee().getGrade() != null ? p.getEmployee().getGrade().getGradeName() : null)
                            .deptName(p.getEmployee().getDept() != null ? p.getEmployee().getDept().getDeptName() : null)
                            .profileImageUrl(p.getEmployee().getEmpProfileImageUrl())
                            .build())
                    .toList();

            String displayName = room.getRoomName();
            if (room.getRoomType() == RoomType.DM && displayName == null) {
                displayName = activeParticipants.stream()
                        .filter(p -> !p.getEmployee().getEmpId().equals(empId))
                        .findFirst()
                        .map(p -> p.getEmployee().getEmpName())
                        .orElse("알 수 없음");
            }

            responses.add(ChatRoomResponse.builder()
                    .roomId(room.getRoomId())
                    .roomType(room.getRoomType().name())
                    .roomName(displayName)
                    .createdByEmpId(room.getCreatedBy().getEmpId())
                    .lastMessageAt(room.getLastMessageAt())
                    .lastMessage(lastMessages.get(room.getRoomId()))
                    .unreadCount(unreadCount)
                    .muted(myParticipant.getIsMuted() != null && myParticipant.getIsMuted())
                    .participants(participantInfos)
                    .build());
        }

        return responses;
    }

    @Transactional
    public ChatRoomResponse createRoom(Long creatorEmpId, ChatRoomCreateRequest request) {
        Employee creator = employeeRepository.findById(creatorEmpId)
                .orElseThrow(() -> new IllegalArgumentException("사원을 찾을 수 없습니다."));

        if (request.getRoomType() == RoomType.DM) {
            if (request.getMemberEmpIds().size() != 1) {
                throw new IllegalArgumentException("1:1 채팅은 상대방 1명만 지정해야 합니다.");
            }
            Long targetEmpId = request.getMemberEmpIds().get(0);
            if (targetEmpId.equals(creatorEmpId)) {
                throw new IllegalArgumentException("자기 자신과의 DM은 생성할 수 없습니다.");
            }

            Optional<ChatRoom> existingDm = chatRoomRepository.findDmRoom(creatorEmpId, targetEmpId);
            if (existingDm.isPresent()) {
                return getMyRooms(creatorEmpId).stream()
                        .filter(r -> r.getRoomId().equals(existingDm.get().getRoomId()))
                        .findFirst()
                        .orElseThrow();
            }
        }

        ChatRoom chatRoom = ChatRoom.builder()
                .roomType(request.getRoomType())
                .roomName(request.getRoomName())
                .createdBy(creator)
                .isActive(true)
                .build();
        chatRoomRepository.save(chatRoom);

        List<Long> allMemberIds = new ArrayList<>(request.getMemberEmpIds());
        if (!allMemberIds.contains(creatorEmpId)) {
            allMemberIds.add(creatorEmpId);
        }

        for (Long empId : allMemberIds) {
            Employee employee = employeeRepository.findById(empId)
                    .orElseThrow(() -> new IllegalArgumentException("사원을 찾을 수 없습니다: " + empId));

            ChatParticipant participant = ChatParticipant.builder()
                    .chatRoom(chatRoom)
                    .employee(employee)
                    .joinedAt(LocalDateTime.now())
                    .isActive(true)
                    .build();
            chatParticipantRepository.save(participant);
            chatRoom.getParticipants().add(participant);
        }

        return getMyRooms(creatorEmpId).stream()
                .filter(r -> r.getRoomId().equals(chatRoom.getRoomId()))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public ChatRoomResponse inviteMembers(Long roomId, Long inviterEmpId, List<Long> memberEmpIds) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        // DM → GROUP 전환
        if (chatRoom.getRoomType() == RoomType.DM) {
            chatRoom.convertToGroup();
        }

        List<ChatParticipant> existing = chatParticipantRepository.findByChatRoom_RoomIdAndIsActiveTrue(roomId);
        java.util.Set<Long> existingEmpIds = existing.stream()
                .map(p -> p.getEmployee().getEmpId())
                .collect(java.util.stream.Collectors.toSet());

        List<String> invitedNames = new ArrayList<>();

        for (Long empId : memberEmpIds) {
            if (existingEmpIds.contains(empId)) continue;

            Employee employee = employeeRepository.findById(empId)
                    .orElseThrow(() -> new IllegalArgumentException("사원을 찾을 수 없습니다: " + empId));

            ChatParticipant participant = ChatParticipant.builder()
                    .chatRoom(chatRoom)
                    .employee(employee)
                    .joinedAt(LocalDateTime.now())
                    .isActive(true)
                    .build();
            chatParticipantRepository.save(participant);
            chatRoom.getParticipants().add(participant);
            invitedNames.add(employee.getEmpName());
        }

        // 참여자 변경 이벤트를 해당 방 구독자에게 브로드캐스트
        if (!invitedNames.isEmpty()) {
            // 변경 후 전체 참여자 목록 조회
            List<ChatParticipant> updatedParticipants = chatParticipantRepository
                    .findByChatRoom_RoomIdAndIsActiveTrue(roomId);

            List<Map<String, Object>> participantList = updatedParticipants.stream()
                    .map(p -> Map.<String, Object>of(
                            "empId", p.getEmployee().getEmpId(),
                            "empName", p.getEmployee().getEmpName(),
                            "gradeName", p.getEmployee().getGrade() != null ? p.getEmployee().getGrade().getGradeName() : "",
                            "deptName", p.getEmployee().getDept() != null ? p.getEmployee().getDept().getDeptName() : ""
                    ))
                    .toList();

            messagingTemplate.convertAndSend(
                    "/sub/chat/room/" + roomId + "/participants",
                    Map.of(
                            "roomId", roomId,
                            "invitedNames", invitedNames,
                            "participants", participantList
                    )
            );
        }

        return getMyRooms(inviterEmpId).stream()
                .filter(r -> r.getRoomId().equals(roomId))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public void renameRoom(Long roomId, Long empId, String newName) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        if (!chatRoom.getCreatedBy().getEmpId().equals(empId)) {
            throw new IllegalArgumentException("채팅방 이름은 생성자만 변경할 수 있습니다.");
        }

        chatRoom.updateRoomName(newName);

        // 참여자 전원에게 이름 변경 이벤트 브로드캐스트
        messagingTemplate.convertAndSend(
                "/sub/chat/room/" + roomId + "/rename",
                Map.of("roomId", roomId, "roomName", newName)
        );
    }

    @Transactional
    public boolean toggleMute(Long roomId, Long empId) {
        ChatParticipant participant = chatParticipantRepository
                .findByChatRoom_RoomIdAndEmployee_EmpIdAndIsActiveTrue(roomId, empId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 참여자가 아닙니다."));
        participant.toggleMute();
        return participant.getIsMuted();
    }

    public void validateParticipant(Long roomId, Long empId) {
        chatParticipantRepository.findByChatRoom_RoomIdAndEmployee_EmpIdAndIsActiveTrue(roomId, empId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 참여자가 아닙니다."));
    }

    @Transactional
    public void leaveRoom(Long roomId, Long empId) {
        ChatParticipant participant = chatParticipantRepository
                .findByChatRoom_RoomIdAndEmployee_EmpIdAndIsActiveTrue(roomId, empId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 참여자가 아닙니다."));

        String leaverName = participant.getEmployee().getEmpName();
        participant.leave();

        // Redis unread 정리
        String key = "unread:user:" + empId + ":room:" + roomId;
        Object unreadObj = chatRedisTemplate.opsForValue().get(key);
        if (unreadObj != null) {
            long unread = Long.parseLong(unreadObj.toString());
            chatRedisTemplate.delete(key);
            String totalKey = "unread:user:" + empId + ":total";
            chatRedisTemplate.opsForValue().decrement(totalKey, unread);
        }

        // 남은 활성 참여자 확인
        List<ChatParticipant> remaining = chatParticipantRepository.findByChatRoom_RoomIdAndIsActiveTrue(roomId);

        if (remaining.isEmpty()) {
            // 모두 나갔으면 방 비활성화
            ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                    .orElseThrow();
            chatRoom.deactivate();
        } else {
            // 남은 참여자에게 퇴장 알림
            List<Map<String, Object>> participantList = remaining.stream()
                    .map(p -> Map.<String, Object>of(
                            "empId", p.getEmployee().getEmpId(),
                            "empName", p.getEmployee().getEmpName(),
                            "gradeName", p.getEmployee().getGrade() != null ? p.getEmployee().getGrade().getGradeName() : "",
                            "deptName", p.getEmployee().getDept() != null ? p.getEmployee().getDept().getDeptName() : ""
                    ))
                    .toList();

            messagingTemplate.convertAndSend(
                    "/sub/chat/room/" + roomId + "/participants",
                    Map.of(
                            "roomId", roomId,
                            "leftName", leaverName,
                            "participants", participantList
                    )
            );
        }
    }

    public ChatRoomResponse findDmRoom(Long myEmpId, Long targetEmpId) {
        Optional<ChatRoom> dmRoom = chatRoomRepository.findDmRoom(myEmpId, targetEmpId);
        if (dmRoom.isEmpty()) {
            return null;
        }
        return getMyRooms(myEmpId).stream()
                .filter(r -> r.getRoomId().equals(dmRoom.get().getRoomId()))
                .findFirst()
                .orElse(null);
    }
}

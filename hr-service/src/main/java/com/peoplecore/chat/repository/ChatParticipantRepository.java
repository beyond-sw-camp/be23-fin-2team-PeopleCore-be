package com.peoplecore.chat.repository;

import com.peoplecore.chat.domain.ChatParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

    Optional<ChatParticipant> findByChatRoom_RoomIdAndEmployee_EmpIdAndIsActiveTrue(Long roomId, Long empId);

    List<ChatParticipant> findByChatRoom_RoomIdAndIsActiveTrue(Long roomId);
}

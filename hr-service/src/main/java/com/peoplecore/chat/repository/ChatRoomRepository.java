package com.peoplecore.chat.repository;

import com.peoplecore.chat.domain.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    @Query("SELECT DISTINCT cr FROM ChatRoom cr " +
            "JOIN FETCH cr.participants cp " +
            "JOIN FETCH cp.employee e " +
            "LEFT JOIN FETCH e.grade " +
            "LEFT JOIN FETCH e.dept " +
            "JOIN FETCH cr.createdBy " +
            "WHERE cr.roomId IN (" +
            "  SELECT cr2.roomId FROM ChatRoom cr2 " +
            "  JOIN cr2.participants cp2 " +
            "  WHERE cp2.employee.empId = :empId AND cp2.isActive = true AND cr2.isActive = true" +
            ") " +
            "ORDER BY cr.lastMessageAt DESC NULLS LAST")
    List<ChatRoom> findMyRoomsWithParticipants(@Param("empId") Long empId);

    @Query("SELECT cr FROM ChatRoom cr " +
            "JOIN cr.participants cp " +
            "WHERE cp.employee.empId = :empId AND cp.isActive = true AND cr.isActive = true " +
            "ORDER BY cr.lastMessageAt DESC NULLS LAST")
    List<ChatRoom> findMyRooms(@Param("empId") Long empId);

    @Query("SELECT cr FROM ChatRoom cr " +
            "JOIN cr.participants cp1 ON cp1.employee.empId = :empId1 AND cp1.isActive = true " +
            "JOIN cr.participants cp2 ON cp2.employee.empId = :empId2 AND cp2.isActive = true " +
            "WHERE cr.roomType = com.peoplecore.chat.domain.RoomType.DM AND cr.isActive = true")
    Optional<ChatRoom> findDmRoom(@Param("empId1") Long empId1, @Param("empId2") Long empId2);
}

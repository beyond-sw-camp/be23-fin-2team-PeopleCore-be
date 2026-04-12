package com.peoplecore.chat.repository;

import com.peoplecore.chat.domain.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("SELECT cm FROM ChatMessage cm " +
            "WHERE cm.chatRoom.roomId = :roomId AND cm.isDeleted = false " +
            "ORDER BY cm.msgId DESC")
    List<ChatMessage> findRecentMessages(@Param("roomId") Long roomId, Pageable pageable);

    @Query("SELECT cm FROM ChatMessage cm " +
            "WHERE cm.chatRoom.roomId = :roomId AND cm.msgId < :beforeMsgId AND cm.isDeleted = false " +
            "ORDER BY cm.msgId DESC")
    List<ChatMessage> findMessagesBefore(@Param("roomId") Long roomId,
                                         @Param("beforeMsgId") Long beforeMsgId,
                                         Pageable pageable);

    @Query("SELECT COUNT(cm) FROM ChatMessage cm " +
            "WHERE cm.chatRoom.roomId = :roomId AND cm.msgId > :lastReadMsgId AND cm.isDeleted = false")
    int countUnread(@Param("roomId") Long roomId, @Param("lastReadMsgId") Long lastReadMsgId);

    Optional<ChatMessage> findTopByChatRoom_RoomIdAndIsDeletedFalseOrderByMsgIdDesc(Long roomId);

    @Query("SELECT cm FROM ChatMessage cm " +
            "WHERE cm.chatRoom.roomId = :roomId AND cm.isDeleted = false " +
            "AND cm.msgContent LIKE CONCAT('%', :keyword, '%') " +
            "ORDER BY cm.msgId DESC")
    List<ChatMessage> searchMessages(@Param("roomId") Long roomId,
                                     @Param("keyword") String keyword,
                                     Pageable pageable);
}

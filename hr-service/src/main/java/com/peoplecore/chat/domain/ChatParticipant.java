package com.peoplecore.chat.domain;

import com.peoplecore.employee.domain.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_participant",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "emp_id"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "participant_id")
    private Long participantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    @Column(name = "last_read_msg_id")
    private Long lastReadMsgId;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_muted", nullable = false)
    @Builder.Default
    private Boolean isMuted = false;

    public void updateLastReadMsgId(Long msgId) {
        this.lastReadMsgId = msgId;
    }

    public void leave() {
        this.isActive = false;
        this.leftAt = LocalDateTime.now();
    }

    public void toggleMute() {
        this.isMuted = !this.isMuted;
    }
}

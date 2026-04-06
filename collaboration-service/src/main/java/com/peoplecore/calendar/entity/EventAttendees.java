package com.peoplecore.calendar.entity;

import com.peoplecore.calendar.enums.InviteStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
  // 일정초대 참석자
public class EventAttendees {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long event_attendees_id;

//   초대받은사원ID - API조회
    private Long invitedEmpId;

//   참석상태
    @Enumerated(EnumType.STRING)
    private InviteStatus inviteStatus;

    private String rejectReason;
    private Boolean isHidden;
    private LocalDateTime invitedAt;
    private LocalDateTime respondedAt;

    @Column(nullable = false)
    private UUID companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn( nullable = false)
    private EventInstances eventInstances;
}

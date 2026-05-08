package com.peoplecore.calendar.entity;

import com.peoplecore.calendar.enums.EventsNotiMethod;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EventsNotifications {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    @Enumerated(EnumType.STRING)
    private EventsNotiMethod eventsNotiMethod;

    private Integer minutesBefore;

//    알림 발송 일시 — 중복 발송 방지용. null 이면 미발송.
    private LocalDateTime sentAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn( nullable = false)
    private Events events;

    public void markSent(LocalDateTime now){
        this.sentAt = now;
    }
}

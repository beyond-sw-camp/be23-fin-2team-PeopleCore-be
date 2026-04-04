package com.peoplecore.calendar.entity;

import com.peoplecore.calendar.enums.EventsNotiMethod;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "events_notifications")
public class EventsNotifications {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    @Enumerated(EnumType.STRING)
    private EventsNotiMethod eventsNotiMethod;

    private Integer minutesBefore;

    @ManyToOne
    @JoinColumn(name = "events_id",nullable = false)
    private Long eventsId;
}

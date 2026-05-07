package com.peoplecore.calendar.dtos;

import com.peoplecore.calendar.entity.EventAttendees;
import com.peoplecore.calendar.entity.Events;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventResDto {

    private Long eventsId;
    private String title;
    private String description;
    private String location;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Boolean isAllDay;
    private Boolean isPublic;
    private Long myCalendarsId;
    private String calendarName;
    private String displayColor;
    private Long empId;
    private LocalDateTime createdAt;
    private List<AttendeeResDto> attendees;

    private RepeatedRulesResDto repeatedRule;
    private List<NotificationResDto> notifications;

    public static EventResDto fromEntity(Events events){
        return fromEntity(events, null);
    }

    public static EventResDto fromEntity(Events events, List<EventAttendees> attendeeList){
        return EventResDto.builder()
                .eventsId(events.getEventsId())
                .title(events.getTitle())
                .description(events.getDescription())
                .location(events.getLocation())
                .startAt(events.getStartAt())
                .endAt(events.getEndAt())
                .isAllDay(events.getIsAllDay())
                .isPublic(events.getIsPublic())
                .myCalendarsId(events.getMyCalendars() != null ? events.getMyCalendars().getMyCalendarsId() : null)
                .calendarName(events.getMyCalendars() != null ? events.getMyCalendars().getCalendarName() : null)
                .displayColor(events.getMyCalendars() != null ? events.getMyCalendars().getMyDisplayColor() : null)
                .empId(events.getEmpId())
                .createdAt(events.getCreatedAt())
                .repeatedRule(events.getRepeatedRules() != null ? RepeatedRulesResDto.fromEntity(events.getRepeatedRules()) : null)
                .notifications(events.getNotifications() != null ? events.getNotifications().stream().map(NotificationResDto::fromEntity).toList() : List.of())
                .attendees(attendeeList != null ? attendeeList.stream().map(AttendeeResDto::fromEntity).toList() : List.of())
                .build();
    }



}

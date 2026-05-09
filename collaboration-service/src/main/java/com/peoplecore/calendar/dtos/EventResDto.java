package com.peoplecore.calendar.dtos;

import com.peoplecore.calendar.entity.EventAttendees;
import com.peoplecore.calendar.entity.Events;
import com.peoplecore.client.dto.EmployeeSimpleResDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    private String creatorName;
    private String creatorDeptName;
    private LocalDateTime createdAt;
    private List<AttendeeResDto> attendees;

    private RepeatedRulesResDto repeatedRule;
    private List<NotificationResDto> notifications;

    public static EventResDto fromEntity(Events events){
        return fromEntity(events, null, Map.of());
    }

    public static EventResDto fromEntity(Events events, List<EventAttendees> attendeeList){
        return fromEntity(events, attendeeList, Map.of());
    }

    /**
     * 사원 정보 주입 빌드.
     * empMap: <empId, EmployeeSimpleResDto> — 작성자 + 모든 참석자 한 번에 일괄 조회한 결과를 Map 으로 전달.
     * 작성자 이름·부서, 참석자 이름·부서를 채움. empMap 이 비어있거나 null 이면 해당 필드는 null.
     */
    public static EventResDto fromEntity(Events events,
                                         List<EventAttendees> attendeeList,
                                         Map<Long, EmployeeSimpleResDto> empMap) {
        EmployeeSimpleResDto creator = empMap == null ? null : empMap.get(events.getEmpId());
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
                .creatorName(creator != null ? creator.getEmpName() : null)
                .creatorDeptName(creator != null ? creator.getDeptName() : null)
                .createdAt(events.getCreatedAt())
                .repeatedRule(events.getRepeatedRules() != null ? RepeatedRulesResDto.fromEntity(events.getRepeatedRules()) : null)
                .notifications(events.getNotifications() != null
                        ? events.getNotifications().stream().map(NotificationResDto::fromEntity).toList()
                        : List.of())
                .attendees(attendeeList != null
                        ? attendeeList.stream()
                            .map(a -> AttendeeResDto.fromEntity(a, empMap == null ? null : empMap.get(a.getInvitedEmpId())))
                            .toList()
                        : List.of())
                .build();
    }



}

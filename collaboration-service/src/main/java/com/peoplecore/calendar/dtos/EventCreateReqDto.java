package com.peoplecore.calendar.dtos;

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
public class EventCreateReqDto
{
    private String title;
    private String description;
    private String location;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Boolean isAllDay;
    private Boolean isPublic;
    private Long myCalendarsId;

//    반복설정
    private RepeatedRulesReqDto repeatedRule;

//    알림 설정
    private List<NotificationReqDto> notifications;

//    참석자
    private List<Long> attendeeEmpIds;

}

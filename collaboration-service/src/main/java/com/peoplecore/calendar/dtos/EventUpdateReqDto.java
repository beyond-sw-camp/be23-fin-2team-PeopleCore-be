package com.peoplecore.calendar.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.boot.archive.scan.internal.ScanResultImpl;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventUpdateReqDto {

    private String title;
    private String description;
    private String location;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Boolean isAllDay;
    private Boolean isPublic;
    private Long myCalendarsId;

    private List<NotificationReqDto> notifications;


}

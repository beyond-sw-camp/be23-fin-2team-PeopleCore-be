package com.peoplecore.calendar.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyCalendarCreateReqDto {

    private String calendarName;
    private String displayColor;
    private Boolean isPublic;
}

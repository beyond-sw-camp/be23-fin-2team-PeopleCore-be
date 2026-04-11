package com.peoplecore.calendar.dtos;

import com.peoplecore.calendar.entity.MyCalendars;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyCalendarResDto {

    private Long myCalendarsId;
    private String calendarName;
    private String displayColor;
    private Boolean isVisible;
    private Integer sortOrder;


    public static MyCalendarResDto fromEntity(MyCalendars c){
        return MyCalendarResDto.builder()
                .myCalendarsId(c.getMyCalendarsId())
                .calendarName(c.getCalendarName())
                .displayColor(c.getMyDisplayColor())
                .isVisible(c.getIsVisible())
                .sortOrder(c.getSortOrder())
                .build();
    }
}

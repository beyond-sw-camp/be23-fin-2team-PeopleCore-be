package com.peoplecore.calendar.dtos;

import lombok.*;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AnnualLeaveSettingResDto {

    private List<LinkedCalendar> calendars;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkedCalendar {
        private Long calendarIds;
        private String calendarName;
        private Boolean isPublic;
    }

    public static AnnualLeaveSettingResDto fromEntity(List<AnnualLeaveSetting> settings) {
        List<LinkedCalendar> calendars = settings.stream()
                .map(s -> LinkedCalendar.builder()
                        .calendarIds(s.getMyCalendar().getMyCalendarsId())
                        .calendarName(s.getMyCalendar().getCalendarName())
                        .isPublic(s.getIsPublic())
                        .build())
                .toList();
        return AnnualLeaveSettingResDto.builder()
                .calendars(calendars)
                .build();
    }
}



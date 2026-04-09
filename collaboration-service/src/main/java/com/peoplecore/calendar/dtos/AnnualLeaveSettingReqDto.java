package com.peoplecore.calendar.dtos;

import lombok.*;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AnnualLeaveSettingReqDto {

//    연동할 캘린더 ID 목록
    private List<CalendarLinkItem> calendars;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CalendarLinkItem{
        private Long calendarId;
        private Boolean isPublic;
    }
}

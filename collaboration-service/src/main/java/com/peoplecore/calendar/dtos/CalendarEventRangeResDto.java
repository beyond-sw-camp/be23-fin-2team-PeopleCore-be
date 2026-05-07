package com.peoplecore.calendar.dtos;

import com.peoplecore.entity.HolidayType;
import com.peoplecore.entity.Holidays;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarEventRangeResDto {

    private List<EventResDto> events;
    private List<HolidayItem> holidays;

    @Getter
    @AllArgsConstructor
    @Builder
    public static class HolidayItem {
    private Long holidayId;
    private LocalDate date;             // 원본
    private LocalDate occurrenceDate;   // 캘린더 표시용 실제 발생일
    private String holidayName;
    private HolidayType holidayType;
    private Boolean isRepeating;

    public static HolidayItem of(Holidays h, LocalDate occurrenceDate) {
        return HolidayItem.builder()
                .holidayId(h.getHolidayId())
                .date(h.getDate())
                .occurrenceDate(occurrenceDate)
                .holidayName(h.getHolidayName())
                .holidayType(h.getHolidayType())
                .isRepeating(h.getIsRepeating())
                .build();
        }
    }
}

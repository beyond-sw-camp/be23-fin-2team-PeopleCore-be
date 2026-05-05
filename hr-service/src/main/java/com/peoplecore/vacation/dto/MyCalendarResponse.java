package com.peoplecore.vacation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.YearMonth;
import java.util.List;

/* 내 캘린더 - 공휴일 + 내 휴가(PENDING/APPROVED) 통합 응답 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyCalendarResponse {

    /* 요청 연월 echo (yyyy-MM) */
    private YearMonth yearMonth;

    /* NATIONAL 우선 dedup, 날짜 ASC */
    private List<CalendarHolidayDto> holidays;

    /* 시작일 ASC, VacationType fetch join */
    private List<VacationRequestResponse> myVacations;
}

package com.peoplecore.vacation.dto;

import com.peoplecore.entity.HolidayType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/* 캘린더 셀 표시용 공휴일 항목 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarHolidayDto {

    /* 표시 날짜 (반복 공휴일은 요청 연도로 매핑된 값) */
    private LocalDate date;

    /* 공휴일/사내일정 명 */
    private String name;

    /* NATIONAL: 법정공휴일, COMPANY: 사내일정 */
    private HolidayType type;

    /* 매년 반복 여부 */
    private Boolean isRepeating;
}

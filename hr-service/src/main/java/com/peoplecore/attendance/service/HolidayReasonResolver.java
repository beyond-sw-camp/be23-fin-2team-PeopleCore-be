package com.peoplecore.attendance.service;

import com.peoplecore.attendance.entity.HolidayReason;
import com.peoplecore.attendance.entity.WorkGroup;
import com.peoplecore.attendance.repository.HolidayLookupRepository;
import com.peoplecore.entity.HolidayType;
import com.peoplecore.entity.Holidays;
import com.peoplecore.vacation.service.BusinessDayCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/* 휴일 사유 판정 - NATIONAL > COMPANY > WEEKLY_OFF, 평일은 null */
/* 1차 월 단위 캐시(BusinessDayCalculator) 로 공휴일 여부 O(1) 판정 - 평일(95%)은 DB 0회 */
/* 2차 공휴일 확정 시에만 findMatching 으로 NATIONAL/COMPANY 타입 구분 */
/* CommuteService(체크인 시) + AttendanceModifyService(주간 그리드 빈 슬롯 fallback) 공통 사용 */
@Component
public class HolidayReasonResolver {

    private final BusinessDayCalculator businessDayCalculator;
    private final HolidayLookupRepository holidayLookupRepository;

    @Autowired
    public HolidayReasonResolver(BusinessDayCalculator businessDayCalculator,
                                 HolidayLookupRepository holidayLookupRepository) {
        this.businessDayCalculator = businessDayCalculator;
        this.holidayLookupRepository = holidayLookupRepository;
    }

    /* 단일 날짜 - 월 단위 공휴일 캐시 1회 로드 */
    public HolidayReason resolve(UUID companyId, LocalDate date, WorkGroup wg) {
        Set<LocalDate> monthHolidays = businessDayCalculator.getHolidaysInMonth(companyId, YearMonth.from(date));
        return resolve(date, wg, monthHolidays);
    }

    /* 미리 로드된 monthHolidays 재사용 - 주간/월간 루프에서 한 번만 로드해 반복 호출 시 DB 0회 */
    /* 공휴일 일자만 findMatching DB 조회 - 평일/주말은 비트마스크 + Set.contains 로 끝 */
    public HolidayReason resolve(LocalDate date, WorkGroup wg, Set<LocalDate> monthHolidays) {
        if (monthHolidays.contains(date)) {
            /* 공휴일 확정 - 타입 구분 위해 단건 조회 (NATIONAL 우선 원칙) */
            return resolveTypeByLookup(date, wg);
        }
        /* 비공휴일 - WorkGroup 비트마스크 (월=bit0 ~ 일=bit6) */
        int bit = 1 << (date.getDayOfWeek().getValue() - 1);
        return (wg.getGroupWorkDay() & bit) != 0 ? null : HolidayReason.WEEKLY_OFF;
    }

    /* 공휴일 확정 시 NATIONAL/COMPANY 단건 조회 헬퍼. WorkGroup 의 Company 에서 companyId 추출 */
    private HolidayReason resolveTypeByLookup(LocalDate date, WorkGroup wg) {
        UUID companyId = wg.getCompany().getCompanyId();
        List<Holidays> matched = holidayLookupRepository.findMatching(
                companyId, date, date.getMonthValue(), date.getDayOfMonth());
        boolean hasNational = matched.stream()
                .anyMatch(h -> h.getHolidayType() == HolidayType.NATIONAL);
        return hasNational ? HolidayReason.NATIONAL : HolidayReason.COMPANY;
    }
}

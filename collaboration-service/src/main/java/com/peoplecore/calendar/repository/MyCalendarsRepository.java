package com.peoplecore.calendar.repository;

import com.peoplecore.calendar.entity.MyCalendars;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MyCalendarsRepository extends JpaRepository<MyCalendars, Long> {
    List<MyCalendars> findByCompanyIdAndEmpIdOrderBySortOrderAsc(UUID companyId, Long empId);

    Boolean existsByCompanyIdAndEmpIdAndCalendarName(UUID companyId, Long empId, String calenderName);

    /* 이름 기준 단건 조회 - 휴가 캘린더 등 시스템 예약 캘린더 lookup 용 */
    Optional<MyCalendars> findByCompanyIdAndEmpIdAndCalendarName(UUID companyId, Long empId, String calendarName);
}

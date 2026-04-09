package com.peoplecore.calendar.repository;

import com.peoplecore.calendar.entity.MyCalendars;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MyCalendarsRepository extends JpaRepository<MyCalendars, Long> {
    List<MyCalendars> findByCompanyIdAndEmpIdOrderBySortOrderAsc(UUID companyId, Long empId);

    Boolean existsByCompanyIdAndEmpIdAndCalendarName(UUID companyId, Long empId, String calenderName);
}

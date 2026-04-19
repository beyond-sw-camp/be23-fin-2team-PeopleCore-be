package com.peoplecore.calendar.repository;

import com.peoplecore.calendar.entity.CalendarShareRequests;
import com.peoplecore.calendar.entity.InterestCalendars;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterestCalendarsRepository extends JpaRepository<InterestCalendars, Long> {

    @Query("SELECT ic FROM InterestCalendars ic JOIN FETCH ic.calendarShareRequest csr WHERE ic.viewerEmpId = :empId AND ic.companyId = :companyId ORDER BY ic.sortOrder ASC")
    List<InterestCalendars> findByViewerEmpIdWithRequest(@Param("empId") Long empId, @Param("companyId") UUID companyID);

    Boolean existsByCompanyIdAndViewerEmpIdAndTargetEmpId(UUID companyId, Long viewEmpId, Long targetEmpId);


    Optional<InterestCalendars> findByCalendarShareRequest(CalendarShareRequests shareRequests);
}

package com.peoplecore.calendar.repository;


import com.peoplecore.calendar.entity.Events;
import jdk.jfr.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface EventsRepository extends JpaRepository<Events, Long>, EventsCustomRepository {


    Page<Events> findByCompanyIdAndIsAllEmployeesTrueAndDeletedAtIsNullOrderByStartAtDesc(UUID companyId, Pageable pageable);


    @Query("""
    SELECT DISTINCT e
    FROM Events e
    JOIN EventInstances ei ON ei.events = e
    JOIN EventAttendees a ON a.eventInstances = ei
    WHERE a.invitedEmpId = :empId
      AND e.companyId = :companyId
      AND e.startAt < :end
      AND e.endAt   > :start
      AND (e.deletedAt IS NULL)
""")
    List<Events> findEventsAttendedByEmp(
            @Param("empId") Long empId,
            @Param("companyId") UUID companyId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}

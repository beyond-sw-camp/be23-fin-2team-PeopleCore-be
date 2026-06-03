package com.peoplecore.calendar.repository;


import com.peoplecore.calendar.entity.Events;
import jdk.jfr.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
      AND (e.deletedAt IS NULL)
      AND (
              (e.startAt < :end AND e.endAt > :start)
              OR (e.repeatedRules IS NOT NULL AND e.startAt < :end)
          )
    """)
    List<Events> findEventsAttendedByEmp(
            @Param("empId") Long empId,
            @Param("companyId") UUID companyId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("SELECT e.eventsId FROM Events e WHERE e.myCalendars.myCalendarsId = :calendarId")
    List<Long> findEventIdsByCalendarId(@Param("calendarId") Long calendarId);

//    이벤트 삭제전 호출. 중복 제거, 반복없는 일정(NULL인 행)은 제외
    @Query("SELECT DISTINCT e.repeatedRules.repeatedRulesId FROM Events e WHERE e.eventsId " +
            "IN :eventIds AND e.repeatedRules IS NOT NULL")
    List<Long> findRepeatedRuleIdsByEventIds(@Param("eventIds") List<Long> eventIds);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Events e WHERE e.eventsId IN :eventIds")
    void deleteByEventIds(@Param("eventIds") List<Long> eventIds);


}

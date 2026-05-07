package com.peoplecore.calendar.repository;

import com.peoplecore.calendar.entity.EventAttendees;
import com.peoplecore.calendar.entity.EventInstances;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventAttendeesRepository extends JpaRepository<EventAttendees, Long> {

    List<EventAttendees> findByEventInstances(EventInstances eventInstances);

    List<EventAttendees> findByEventInstances_Events_EventsId(Long eventsId);

    @Query("SELECT a FROM EventAttendees a WHERE a.eventInstances.events.eventsId IN :eventIds")
    List<EventAttendees> findByEventIds(@Param("eventIds") List<Long> eventIds);

    void deleteByEventInstances(EventInstances eventInstances);
}

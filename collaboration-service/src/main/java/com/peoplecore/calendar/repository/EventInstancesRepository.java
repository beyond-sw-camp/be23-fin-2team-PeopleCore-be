package com.peoplecore.calendar.repository;

import com.peoplecore.calendar.entity.EventInstances;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventInstancesRepository extends JpaRepository<EventInstances, Long> {

    EventInstances findFirstByEvents_EventsId(Long eventsId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM EventInstances ei WHERE ei.events.eventsId IN :eventIds")
    void deleteByEventIds(@Param("eventIds") List<Long> eventIds);
}

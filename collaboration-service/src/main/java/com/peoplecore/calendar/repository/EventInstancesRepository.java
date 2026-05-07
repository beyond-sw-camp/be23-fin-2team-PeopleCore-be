package com.peoplecore.calendar.repository;

import com.peoplecore.calendar.entity.EventInstances;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventInstancesRepository extends JpaRepository<EventInstances, Long> {

    EventInstances findFirstByEvents_EventsId(Long eventsId);

    void deleteByEvents_EventsId(Long eventsId);
}

package com.peoplecore.calendar.repository;

import com.peoplecore.calendar.entity.EventsNotifications;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventsNotificationsRepository extends JpaRepository<EventsNotifications, Long> {

    List<EventsNotifications> findByEvents_EventsId(Long eventsId);

    void deleteByEvents_EventsId(Long eventsId);
}

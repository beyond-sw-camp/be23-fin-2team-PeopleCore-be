package com.peoplecore.calendar.repository;

import com.peoplecore.calendar.entity.EventsNotifications;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EventsNotificationsRepository extends JpaRepository<EventsNotifications, Long> {

    List<EventsNotifications> findByEvents_EventsId(Long eventsId);

    void deleteByEvents_EventsId(Long eventsId);

//    트리거 시각이 지났고 아직 발송되지 않은 알림 — 스케줄러용
//    트리거 시각 = events.startAt - minutesBefore 분
    @Query("""
        SELECT n FROM EventsNotifications n
        WHERE n.sentAt IS NULL
          AND n.events.deletedAt IS NULL
          AND n.events.startAt > :now
          AND FUNCTION('TIMESTAMPADD', MINUTE, -n.minutesBefore, n.events.startAt) <= :now
    """)
    List<EventsNotifications> findDueAlarms(@Param("now") LocalDateTime now);
}

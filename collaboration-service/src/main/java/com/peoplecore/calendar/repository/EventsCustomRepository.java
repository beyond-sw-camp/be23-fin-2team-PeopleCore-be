package com.peoplecore.calendar.repository;

import com.peoplecore.calendar.entity.Events;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface EventsCustomRepository {

    //    내캘린더 일정 조회(기간필터)
    List<Events> findByCalendarIdsAndPeriod(List<Long> calendarIds, UUID companyId, LocalDateTime start, LocalDateTime end);

    //    전사 일정 조회
    List<Events> findCompanyEvents(UUID companyId, LocalDateTime start, LocalDateTime end)  ;

    //    타인 공개 일정 조회(관심캘린더용)
    List<Events> findPublicEventsByEmpId(Long targetEmpId, UUID companyId, LocalDateTime start, LocalDateTime end);

    //   예약 알림 스케줄러용 - 알림설정이 있는 일정 조회
    List<Events> findEventsWithNotificationsBetween(LocalDateTime from, LocalDateTime to);

}

package com.peoplecore.calendar.repository;

import com.peoplecore.calendar.entity.Events;
import com.peoplecore.calendar.entity.QEvents;
import com.peoplecore.calendar.entity.QEventsNotifications;
import com.peoplecore.calendar.entity.QMyCalendars;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class EventsCustomRepositoryImpl implements EventsCustomRepository{

    private final JPAQueryFactory queryFactory;

    private final QEvents event = QEvents.events;
    private final QMyCalendars calendar = QMyCalendars.myCalendars;
    private final QEventsNotifications notification = QEventsNotifications.eventsNotifications;

    @Autowired
    public EventsCustomRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

// 내캘린더 일정 조회
    @Override
    public List<Events> findByCalendarIdsAndPeriod(List<Long> calendarIds, UUID companyId, LocalDateTime start, LocalDateTime end) {
        return queryFactory
                .selectFrom(event)
                .join(event.myCalendars, calendar).fetchJoin()
                .leftJoin(event.notifications, notification).fetchJoin()
                .where(
                        calendar.myCalendarsId.in(calendarIds),
                        companyIdEq(companyId),
                        notDeleted(),
                        periodOverlap(start, end)
                )
                .orderBy(event.startAt.asc())
                .fetch();
    }


//    전사일정조회
    @Override
    public List<Events> findCompanyEvents(UUID companyId, LocalDateTime start, LocalDateTime end) {
        return queryFactory
                .selectFrom(event)
                .leftJoin(event.notifications, notification).fetchJoin()
                .where(
                        companyIdEq(companyId),
                        event.isAllEmployees.isTrue(),
                        notDeleted(),
                        periodOverlap(start, end)
                )
                .orderBy(event.startAt.asc())
                .fetch();
    }


    //    타인 공개 일정 조회(관심캘린더용)
    @Override
    public List<Events> findPublicEventsByEmpId(Long targetEmpId, UUID companyId, LocalDateTime start, LocalDateTime end) {
        return queryFactory.selectFrom(event)
                .join(event.myCalendars, calendar).fetchJoin()
                .leftJoin(event.notifications, notification).fetchJoin()
                .where(
                        calendar.empId.eq(targetEmpId),
                        companyIdEq(companyId),
                        calendar.isPublic.isTrue(),
                        event.isPublic.isTrue(),
                        notDeleted(),
                        periodOverlap(start, end)
                )
                .orderBy(event.startAt.asc())
                .fetch();
    }


    //   예약 알림 스케줄러용 - 알림설정이 있는 일정 조회
    @Override
    public List<Events> findEventsWithNotificationsBetween(LocalDateTime from, LocalDateTime to) {
        return queryFactory
                .selectFrom(event).distinct()
                .join(event.notifications, notification).fetchJoin()
                .where(
                        notDeleted(),
                        event.startAt.between(from, to)
                )
                .fetch();
    }




    private BooleanExpression companyIdEq(UUID companyId){
        return companyId != null ? event.companyId.eq(companyId) : null;
    }
    private BooleanExpression notDeleted(){
        return event.deletedAt.isNull();
    }

//    일정 조회 범위와 겹치는지 확인 (일정시작 < 조회 끝) AND (일정 끝 > 조회시작)
    private BooleanExpression periodOverlap(LocalDateTime start, LocalDateTime end){
        return event.startAt.lt(end).and(event.endAt.gt(start));
    }

}

package com.peoplecore.calendar.scheduler;

import com.peoplecore.alarm.publisher.AlarmEventPublisher;
import com.peoplecore.calendar.entity.EventAttendees;
import com.peoplecore.calendar.entity.Events;
import com.peoplecore.calendar.entity.EventsNotifications;
import com.peoplecore.calendar.repository.EventAttendeesRepository;
import com.peoplecore.calendar.repository.EventsNotificationsRepository;
import com.peoplecore.event.AlarmEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class CalendarAlarmScheduler {

    private final EventsNotificationsRepository eventsNotificationsRepository;
    private final EventAttendeesRepository eventAttendeesRepository;
    private final AlarmEventPublisher alarmEventPublisher;

//    매분 0초에 실행 — 트리거 시각에 도달한 알림 발송
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void dispatchDueAlarms(){
        LocalDateTime now = LocalDateTime.now();
        List<EventsNotifications> due = eventsNotificationsRepository.findDueAlarms(now);
        if (due.isEmpty()) return;

        log.info("[CalendarAlarmScheduler] 발송 대상 알림 {}건", due.size());

        for (EventsNotifications n : due) {
            try {
                Events event = n.getEvents();
                List<Long> targetEmpIds = collectTargetEmpIds(event);
                if (targetEmpIds.isEmpty()) {
                    n.markSent(now);
                    continue;
                }

                alarmEventPublisher.publisher(AlarmEvent.builder()
                        .companyId(event.getCompanyId())
                        .alarmType("CalendarReminder")
                        .alarmTitle(event.getTitle())
                        .alarmContent(formatTimeBefore(n.getMinutesBefore()) + " 일정이 시작됩니다.")
                        .alarmLink("/calendar?eventId=" + event.getEventsId())
                        .alarmRefType("EVENT")
                        .alarmRefId(event.getEventsId())
                        .empIds(targetEmpIds)
                        .build());

                n.markSent(now);
            } catch (Exception ex) {
                log.error("[CalendarAlarmScheduler] 알림 발송 실패 notificationId={}: {}", n.getNotificationId(), ex.getMessage(), ex);
            }
        }
    }

//    작성자 + 참석자 (중복 제거)
    private List<Long> collectTargetEmpIds(Events event){
        Set<Long> targets = new LinkedHashSet<>();
        if (event.getEmpId() != null) targets.add(event.getEmpId());

        List<EventAttendees> attendees = eventAttendeesRepository.findByEventInstances_Events_EventsId(event.getEventsId());
        for (EventAttendees a : attendees) {
            if (a.getInvitedEmpId() != null) targets.add(a.getInvitedEmpId());
        }
        return new ArrayList<>(targets);
    }


    /** 분 단위 minutesBefore 를 사용자 친화적 문자열로 역변환.
     *  1440 의 배수 → "N일 후", 60 의 배수 → "N시간 후", 그 외 → "N분 후" */
    private static String formatTimeBefore(int minutesBefore){
        if (minutesBefore >= 1440 && minutesBefore % 1440 == 0) {
            return (minutesBefore / 1440) + "일 후";
        }
        if (minutesBefore >= 60 && minutesBefore % 60 == 0) {
            return (minutesBefore / 60) + "시간 후";
        }
        return minutesBefore + "분 후";
    }
}

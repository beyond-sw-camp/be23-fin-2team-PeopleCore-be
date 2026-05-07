package com.peoplecore.calendar.service;

import com.peoplecore.alarm.publisher.AlarmEventPublisher;
import com.peoplecore.alarm.service.AlarmService;
import com.peoplecore.calendar.dtos.*;
import com.peoplecore.calendar.entity.*;
import com.peoplecore.calendar.repository.*;
import com.peoplecore.entity.Holidays;
import com.peoplecore.event.AlarmEvent;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static com.peoplecore.calendar.entity.QEvents.events;

@Slf4j
@Service
@Transactional(readOnly = true)
public class CalendarEventService {

    private final MyCalendarsRepository myCalendarsRepository;
    private final EventsRepository eventsRepository;
    private final RepeatedRulesRepository repeatedRulesRepository;
    private final EventsNotificationsRepository eventsNotificationsRepository;
     private final InterestCalendarsRepository interestCalendarsRepository;
     private final AlarmEventPublisher alarmEventPublisher;
     private final HolidayRepository holidayRepository;


    @Autowired
    public CalendarEventService(MyCalendarsRepository myCalendarsRepository, EventsRepository eventsRepository, RepeatedRulesRepository repeatedRulesRepository, EventsNotificationsRepository eventsNotificationsRepository, InterestCalendarsRepository interestCalendarsRepository, AlarmEventPublisher alarmEventPublisher, HolidayRepository holidayRepository) {
        this.myCalendarsRepository = myCalendarsRepository;
        this.eventsRepository = eventsRepository;
        this.repeatedRulesRepository = repeatedRulesRepository;
        this.eventsNotificationsRepository = eventsNotificationsRepository;
        this.interestCalendarsRepository = interestCalendarsRepository;
        this.alarmEventPublisher = alarmEventPublisher;
        this.holidayRepository = holidayRepository;
    }

//    일정 등록
    @Transactional
    public EventResDto createEvent(UUID companyId, Long empId, EventCreateReqDto reqDto){
        MyCalendars calendar = myCalendarsRepository.findById(reqDto.getMyCalendarsId()).orElseThrow(()-> new CustomException(ErrorCode.CALENDAR_NOT_FOUND));

        validateCalendarOwner(calendar,empId);

//        반복규칙 저장 (없으면 null)
        RepeatedRules repeatedRules = saveRepeatedRule(companyId, reqDto.getRepeatedRule());

        Events event = Events.builder()
                .empId(empId)
                .title(reqDto.getTitle())
                .description(reqDto.getDescription())
                .location(reqDto.getLocation())
                .startAt(reqDto.getStartAt())
                .endAt(reqDto.getEndAt())
                .isAllDay(reqDto.getIsAllDay())
                .isPublic(reqDto.getIsPublic())
                .isAllEmployees(false)  //전사 일정 생성 불가
                .companyId(companyId)
                .myCalendars(calendar)  //개인 캘린더
                .repeatedRules(repeatedRules)
                .build();

        eventsRepository.save(event);

//        알림설정 저장
        saveNotifications(event, reqDto.getNotifications());

//        참석자에게 즉시알림발송
        sendAttendeeAlarm(companyId, event, reqDto.getAttendeeEmpIds());

        return EventResDto.fromEntity(event);
    }


//    일정 수정
    @Transactional
    public EventResDto updateEvent(UUID companyId, Long empId, Long eventsId, EventUpdateReqDto reqDto){
        Events event = findEventOrThrow(eventsId, companyId);
        validateEventOwner(event, empId);

        MyCalendars calendars = myCalendarsRepository.findById(reqDto.getMyCalendarsId()).orElseThrow(()-> new CustomException(ErrorCode.CALENDAR_NOT_FOUND));

        event.update(
                reqDto.getTitle(), reqDto.getDescription(), reqDto.getLocation(), reqDto.getStartAt(), reqDto.getEndAt(), reqDto.getIsAllDay(), reqDto.getIsPublic(), calendars
        );

//        알림 갱신
        eventsNotificationsRepository.deleteByEvents_EventsId(eventsId);
        saveNotifications(event, reqDto.getNotifications());

        return EventResDto.fromEntity(event);
    }


//     일정 삭제
    @Transactional
    public void deleteEvent(UUID companyId, Long empId, Long eventsId){
        Events event = findEventOrThrow(eventsId, companyId);
        validateEventOwner(event, empId);
        event.softDelete();
    }

//  일정 상세 조회
    public EventResDto getEvent(UUID companyId, Long eventsId){
        Events event = findEventOrThrow(eventsId, companyId);
        return EventResDto.fromEntity(event);
    }

//    캘린더 뷰 일정조회 (월,주,일)
//    내캘린더 + 관심캘린더 + 전사일정통합
    public CalendarEventRangeResDto getEventsForView(UUID companyId, Long empId, LocalDateTime start, LocalDateTime end) {

//        1. 내캘린더 ID 추출 - 보이기 설정값
        List<MyCalendars> myCalendars = myCalendarsRepository.findByCompanyIdAndEmpIdOrderBySortOrderAsc(companyId, empId);
        List<Long> visibleCalIds = myCalendars.stream().filter(c -> Boolean.TRUE.equals(c.getIsVisible())).map(MyCalendars::getMyCalendarsId).toList();

//        2. 내일정        //List.of() : 빈리스트 반환
        List<Events> myEvents = visibleCalIds.isEmpty() ? List.of() : eventsRepository.findByCalendarIdsAndPeriod(visibleCalIds, companyId, start, end);

        log.info("=== A 진입 ===");
        log.info("empId={}, companyId={}", empId, companyId);
//        3. 관심캘린더 일정 (공개일정만)
        List<InterestCalendars> interestCalendars = interestCalendarsRepository.findByViewerEmpIdWithRequest(empId, companyId);
        log.info("interestCalendars count={}", interestCalendars.size());
        List<Events> interestEvents = interestCalendars.stream()
                .filter(ic -> Boolean.TRUE.equals(ic.getIsVisible()))
                .flatMap(ic -> eventsRepository.findPublicEventsByEmpId(ic.getTargetEmpId(), companyId, start, end).stream())
                .toList();
        log.info("interestEvents 총={}", interestEvents.size());

//        4. 전사일정
        List<Events> companyEvents = eventsRepository.findCompanyEvents(companyId, start, end);

//        5. 통합(중복 제거)
        Map<Long, Events> merged = new LinkedHashMap<>();

        for (Events e : myEvents) {
            merged.putIfAbsent(e.getEventsId(), e);
        }
        for (Events e : interestEvents) {
            merged.putIfAbsent(e.getEventsId(), e);
        }
        for (Events e : companyEvents) {
            merged.putIfAbsent(e.getEventsId(), e);
        }

        List<EventResDto> result = new ArrayList<>();
        for (Events e : merged.values()) {
            result.add(EventResDto.fromEntity(e));
        }

        // 6) 공휴일 머지 (NATIONAL + 회사의 COMPANY)
        LocalDate startDate = start.toLocalDate();
        LocalDate endDate   = end.toLocalDate();
        List<Holidays> rawHolidays =
                holidayRepository.findByCompanyIdAndPeriod(companyId, startDate, endDate);

        List<CalendarEventRangeResDto.HolidayItem> holidays = new ArrayList<>();
        for (Holidays h : rawHolidays) {
            if (Boolean.TRUE.equals(h.getIsRepeating())) {
                // 반복 휴일은 [startDate~endDate] 범위 내의 모든 발생일을 펼침
                int month = h.getDate().getMonthValue();
                int day   = h.getDate().getDayOfMonth();
                for (int year = startDate.getYear(); year <= endDate.getYear(); year++) {
                    LocalDate occ;
                    try {
                        occ = LocalDate.of(year, month, day);
                    } catch (Exception e) {
                        occ = LocalDate.of(year, month, 28); // 2/29 평년 fallback
                    }
                    if (!occ.isBefore(startDate) && !occ.isAfter(endDate)) {
                        holidays.add(CalendarEventRangeResDto.HolidayItem.of(h, occ));
                    }
                }
            } else {
                LocalDate d = h.getDate();
                if (!d.isBefore(startDate) && !d.isAfter(endDate)) {
                    if (!d.isBefore(startDate) && !d.isAfter(endDate)) {
                        holidays.add(CalendarEventRangeResDto.HolidayItem.of(h, d));
                    }
                }
            }
        }

        return CalendarEventRangeResDto.builder()
                .events(result)
                .holidays(holidays)
                .build();

    }


    private Events findEventOrThrow(Long eventsId, UUID companyId){
        Events events = eventsRepository.findById(eventsId).orElseThrow(()-> new CustomException(ErrorCode.EVENT_NOT_FOUND));

        if (!events.getCompanyId().equals(companyId)){
            throw new CustomException(ErrorCode.EVENT_ACCESS_DENIED);
        }
        if(events.isDelete()){
            throw new CustomException(ErrorCode.EVENT_DELETED);
        }
        return events;
    }

    private void validateEventOwner(Events events, Long empId){
        if(!events.getEmpId().equals(empId)) {
            throw new CustomException(ErrorCode.EVENT_OWNER_MISMATCH);
        }
    }

    private void validateCalendarOwner(MyCalendars myCalendars, Long empId){
        if( !myCalendars.getEmpId().equals(empId)){
            throw new CustomException(ErrorCode.EVENT_REGISTER_DENIED);
        }
    }

//반복규칙 저장
    private RepeatedRules saveRepeatedRule(UUID companyId, RepeatedRulesReqDto reqDto){
        if(reqDto == null || reqDto.getFrequency() == null){
            return null;
        }
        return repeatedRulesRepository.save(
                RepeatedRules.builder()
                        .frequency(reqDto.getFrequency())
                        .intervalVal(reqDto.getIntervalVal())
                        .byDay(reqDto.getByDay())
                        .byMonthDay(reqDto.getByMonthDay())
                        .until(reqDto.getUntil())
                        .count(reqDto.getCount())
                        .companyId(companyId)
                        .build()
        );
    }

//    알림설정 저장
    private void saveNotifications(Events event, List<NotificationReqDto> reqDtos){
        if(reqDtos == null || reqDtos.isEmpty()) return;

        List<EventsNotifications> notificationsList = reqDtos.stream()
                .map(r -> EventsNotifications.builder()
                        .eventsNotiMethod(r.getMethod())
                        .minutesBefore(r.getMinutesBefore())
                        .events(event)
                        .build())
                .toList();
        eventsNotificationsRepository.saveAll(notificationsList);

    }

//    일정참석자에게 알림발송 / kafka 비동기
    private void sendAttendeeAlarm(UUID companyId, Events event, List<Long> attendeeEmpIds){
        if(attendeeEmpIds == null  || attendeeEmpIds.isEmpty()) return;

        alarmEventPublisher.publisher(AlarmEvent.builder()
                .companyId(companyId)
                .alarmType("Calendar")
                .alarmContent(event.getTitle() + " 일정에 초대되었습니다")
                .alarmLink("/calendar?eventId=" + event.getEventsId())
                .alarmRefType("EVENT")
                .alarmRefId(event.getEventsId())
                .empIds(attendeeEmpIds)
                .build());
    }
}




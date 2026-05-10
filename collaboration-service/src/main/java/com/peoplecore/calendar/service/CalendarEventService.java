package com.peoplecore.calendar.service;

import com.peoplecore.alarm.publisher.AlarmEventPublisher;
import com.peoplecore.alarm.service.AlarmService;
import com.peoplecore.calendar.dtos.*;
import com.peoplecore.calendar.entity.*;
import com.peoplecore.calendar.enums.EventInstancesType;
import com.peoplecore.calendar.enums.InviteStatus;
import com.peoplecore.calendar.repository.*;
import com.peoplecore.client.component.HrCacheService;
import com.peoplecore.client.dto.EmployeeSimpleResDto;
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
import java.util.stream.Collectors;

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
     private final EventInstancesRepository eventInstancesRepository;
     private final EventAttendeesRepository eventAttendeesRepository;
     private final HrCacheService hrCacheService;


    @Autowired
    public CalendarEventService(MyCalendarsRepository myCalendarsRepository, EventsRepository eventsRepository, RepeatedRulesRepository repeatedRulesRepository, EventsNotificationsRepository eventsNotificationsRepository, InterestCalendarsRepository interestCalendarsRepository, AlarmEventPublisher alarmEventPublisher, HolidayRepository holidayRepository, EventInstancesRepository eventInstancesRepository, EventAttendeesRepository eventAttendeesRepository, HrCacheService hrCacheService) {
        this.myCalendarsRepository = myCalendarsRepository;
        this.eventsRepository = eventsRepository;
        this.repeatedRulesRepository = repeatedRulesRepository;
        this.eventsNotificationsRepository = eventsNotificationsRepository;
        this.interestCalendarsRepository = interestCalendarsRepository;
        this.alarmEventPublisher = alarmEventPublisher;
        this.holidayRepository = holidayRepository;
        this.eventInstancesRepository = eventInstancesRepository;
        this.eventAttendeesRepository = eventAttendeesRepository;
        this.hrCacheService = hrCacheService;
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

// 1) EventInstances 1건 생성 (단일 일정 또는 반복 마스터)
        EventInstances instance = EventInstances.builder()
                .events(event)
                .companyId(companyId)
                .originalStart(event.getStartAt())
                .startAt(event.getStartAt())
                .endAt(event.getEndAt())
                .eventInstancesType(repeatedRules == null ? EventInstancesType.SINGLE : EventInstancesType.REPEATED)
                .isCancelled(false)
                .build();
        eventInstancesRepository.save(instance);

// 2) 참석자 저장
        List<Long> attendeeEmpIds = sanitizeAttendeeEmpIds(reqDto.getAttendeeEmpIds(), empId);
        saveAttendees(companyId, instance, attendeeEmpIds);

//        알림설정 저장
        saveNotifications(event, reqDto.getNotifications());

//        참석자에게 즉시알림발송
        sendAttendeeAlarm(companyId, event, attendeeEmpIds);

        List<EventAttendees> savedAttendees = eventAttendeesRepository.findByEventInstances(instance);
        return EventResDto.fromEntity(event, savedAttendees, loadEmployeeMap(event, savedAttendees));
    }


//    일정 수정
    @Transactional
    public EventResDto updateEvent(UUID companyId, Long empId, Long eventsId, EventUpdateReqDto reqDto){
        Events event = findEventOrThrow(eventsId, companyId);
        validateEventOwner(event, empId);

        MyCalendars calendars = myCalendarsRepository.findById(reqDto.getMyCalendarsId()).orElseThrow(()-> new CustomException(ErrorCode.CALENDAR_NOT_FOUND));

        // 반복 규칙 갱신: 신규 저장 → Events FK 교체 → 옛 규칙 삭제 (FK 위반 방지)
        RepeatedRules oldRule = event.getRepeatedRules();
        RepeatedRules newRule = saveRepeatedRule(companyId, reqDto.getRepeatedRule());

        event.update(
                reqDto.getTitle(), reqDto.getDescription(), reqDto.getLocation(), reqDto.getStartAt(), reqDto.getEndAt(), reqDto.getIsAllDay(), reqDto.getIsPublic(), calendars, newRule
        );

        if (oldRule != null && (newRule == null || !oldRule.getRepeatedRulesId().equals(newRule.getRepeatedRulesId()))) {
            repeatedRulesRepository.delete(oldRule);
        }

//        알림 갱신
        eventsNotificationsRepository.deleteByEvents_EventsId(eventsId);
        saveNotifications(event, reqDto.getNotifications());

        // 참석자 갱신: 기존 삭제 후 재등록 (소량이면 OK, 변경 diff 방식으로도 가능)
        EventInstances instance = eventInstancesRepository.findFirstByEvents_EventsId(eventsId);
        if (instance != null) {
            eventAttendeesRepository.deleteByEventInstances(instance);
            List<Long> attendeeEmpIds = sanitizeAttendeeEmpIds(reqDto.getAttendeeEmpIds(), empId);
            saveAttendees(companyId, instance, attendeeEmpIds);
            // 새로 추가된 참석자에게만 초대 알림 발송
            sendAttendeeAlarm(companyId, event, attendeeEmpIds);
        }

        List<EventAttendees> currentAttendees = eventAttendeesRepository.findByEventInstances_Events_EventsId(eventsId);
        return EventResDto.fromEntity(event, currentAttendees, loadEmployeeMap(event, currentAttendees));
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
        List<EventAttendees> attendees = eventAttendeesRepository.findByEventInstances_Events_EventsId(eventsId);
        return EventResDto.fromEntity(event, attendees, loadEmployeeMap(event, attendees));
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

//        5. 내가 초대받은 일정 (참석자)
        List<Events> invitedEvents = eventsRepository.findEventsAttendedByEmp(empId, companyId, start, end);

//        6. 통합(중복 제거)
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
        for (Events e : invitedEvents) {
            merged.putIfAbsent(e.getEventsId(), e);
        }

//        7. 참석자 일괄 조회 (eventId → 참석자 목록 매핑)
        Map<Long, List<EventAttendees>> attendeesByEventId = new HashMap<>();
        List<EventAttendees> allAttendees = List.of();
        if (!merged.isEmpty()) {
            allAttendees = eventAttendeesRepository.findByEventIds(new ArrayList<>(merged.keySet()));
            for (EventAttendees a : allAttendees) {
                Long eid = a.getEventInstances().getEvents().getEventsId();
                attendeesByEventId.computeIfAbsent(eid, k -> new ArrayList<>()).add(a);
            }
        }

//        7-1. 모든 일정의 작성자 + 참석자 empId 통합 후 사원 정보 1회 일괄 조회 (N+1 방지)
        Set<Long> allEmpIds = new HashSet<>();
        for (Events e : merged.values()) {
            if (e.getEmpId() != null) allEmpIds.add(e.getEmpId());
        }
        for (EventAttendees a : allAttendees) {
            if (a.getInvitedEmpId() != null) allEmpIds.add(a.getInvitedEmpId());
        }
        Map<Long, EmployeeSimpleResDto> empMap = Map.of();
        if (!allEmpIds.isEmpty()) {
            try {
                empMap = hrCacheService.getEmployees(new ArrayList<>(allEmpIds)).stream()
                        .collect(Collectors.toMap(EmployeeSimpleResDto::getEmpId, e -> e, (a, b) -> a));
            } catch (Exception ex) {
                log.warn("사원 정보 일괄 조회 실패 - 이름·부서 비워서 응답 empIds={}, error={}", allEmpIds, ex.getMessage());
            }
        }

        List<EventResDto> result = new ArrayList<>();
        for (Events e : merged.values()) {
            result.add(EventResDto.fromEntity(e, attendeesByEventId.get(e.getEventsId()), empMap));
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


    /** 작성자 + 참석자 empId 들을 일괄 조회해 Map<empId, dto> 반환. 단일 일정 조회/생성/수정용.
     *  hr-service 가 일시적으로 응답하지 않아도 일정 저장 트랜잭션을 롤백시키지 않도록 예외는 흡수하고 빈 맵 반환. */
    private Map<Long, EmployeeSimpleResDto> loadEmployeeMap(Events event, List<EventAttendees> attendees) {
        Set<Long> empIds = new HashSet<>();
        if (event.getEmpId() != null) empIds.add(event.getEmpId());
        if (attendees != null) {
            for (EventAttendees a : attendees) {
                if (a.getInvitedEmpId() != null) empIds.add(a.getInvitedEmpId());
            }
        }
        if (empIds.isEmpty()) return Map.of();
        try {
            return hrCacheService.getEmployees(new ArrayList<>(empIds)).stream()
                    .collect(Collectors.toMap(EmployeeSimpleResDto::getEmpId, e -> e, (a, b) -> a));
        } catch (Exception e) {
            log.warn("사원 정보 일괄 조회 실패 - 이름·부서 비워서 응답 empIds={}, error={}", empIds, e.getMessage());
            return Map.of();
        }
    }

    private void saveAttendees(UUID companyId, EventInstances instance, List<Long> attendeeEmpIds){
        if (attendeeEmpIds == null || attendeeEmpIds.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        List<EventAttendees> rows = attendeeEmpIds.stream()
                .map(eid -> EventAttendees.builder()
                        .eventInstances(instance)
                        .invitedEmpId(eid)
                        .inviteStatus(InviteStatus.PENDING)
                        .isHidden(false)
                        .invitedAt(now)
                        .companyId(companyId)
                        .build())
                .toList();
        eventAttendeesRepository.saveAll(rows);
    }

    private List<Long> sanitizeAttendeeEmpIds(List<Long> attendeeEmpIds, Long ownerEmpId) {
        if (attendeeEmpIds == null || attendeeEmpIds.isEmpty()) return List.of();
        return attendeeEmpIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.equals(ownerEmpId))
                .distinct()
                .toList();
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
                .alarmTitle("일정 초대")
                .alarmContent(event.getTitle() + " 일정에 초대되었습니다")
                .alarmLink("/calendar?eventId=" + event.getEventsId())
                .alarmRefType("EVENT")
                .alarmRefId(event.getEventsId())
                .empIds(attendeeEmpIds)
                .build());
    }
}



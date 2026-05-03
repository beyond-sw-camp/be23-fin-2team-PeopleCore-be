package com.peoplecore.calendar.service;

import com.peoplecore.alarm.publisher.AlarmEventPublisher;
import com.peoplecore.calendar.dtos.CompanyEventReqDto;
import com.peoplecore.calendar.dtos.CompanyEventResDto;
import com.peoplecore.calendar.entity.Events;
import com.peoplecore.calendar.repository.EventsRepository;
import com.peoplecore.client.component.HrCacheService;
import com.peoplecore.client.dto.EmployeeSimpleResDto;
import com.peoplecore.event.AlarmEvent;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CompanyEventService {

    private final EventsRepository eventsRepository;
    private final HrCacheService hrCacheService;
    private final AlarmEventPublisher alarmEventPublisher;

    @Autowired
    public CompanyEventService(EventsRepository eventsRepository, HrCacheService hrCacheService, AlarmEventPublisher alarmEventPublisher) {
        this.eventsRepository = eventsRepository;
        this.hrCacheService = hrCacheService;
        this.alarmEventPublisher = alarmEventPublisher;
    }

    //    전사 일정 등록
    @Transactional
    public CompanyEventResDto createCompanyEvent(UUID companyId, Long empId, CompanyEventReqDto reqDto) {
        Events event = Events.builder()
                .empId(empId)
                .title(reqDto.getTitle())
                .description(reqDto.getDescription())
                .location(reqDto.getLocation())
                .startAt(reqDto.getStartAt())
                .endAt(reqDto.getEndAt())
                .isAllDay(reqDto.getIsAllDay())
                .isPublic(true)
                .isAllEmployees(true)
                .companyId(companyId)
                .myCalendars(null)       //개인캘린더 종속 X
                .build();

        eventsRepository.save(event);

//        전직원에게 알림
        alarmEventPublisher.publisher(AlarmEvent.builder()
                .companyId(companyId)
                .alarmType("Calendar")
                .alarmTitle("전사 일정 등록")
                .alarmContent(reqDto.getTitle() + " 전사 일정이 등록되었습니다")
                .alarmLink("/calendar?eventId=" + event.getEventsId()).alarmRefType("COMPANY_EVENT")
                .alarmRefId(event.getEmpId())
                .empIds(List.of())
                .build());

        return CompanyEventResDto.fromEntity(event, getCreatorName(empId));
    }


    //  전사일정 수정
    @Transactional
    public CompanyEventResDto updateCompanyEvent(UUID companyId, Long empId, Long eventsId, CompanyEventReqDto reqDto) {
        Events event = findCompanyEventOrThrow(eventsId, companyId);

        event.updateCompanyEvent(
                reqDto.getTitle(),
                reqDto.getDescription(),
                reqDto.getLocation(),
                reqDto.getStartAt(),
                reqDto.getEndAt(),
                reqDto.getIsAllDay()
        );

        return CompanyEventResDto.fromEntity(event, getCreatorName(event.getEmpId()));
    }


//    전사일정 삭제
    @Transactional
    public void deleteCompanyEvent(UUID companyId, Long eventsId){
        Events events = findCompanyEventOrThrow(eventsId, companyId);
        events.softDelete();
    }


    private String getCreatorName(Long empId){
        List<EmployeeSimpleResDto> emps = hrCacheService.getEmployees(List.of(empId));
        return emps.isEmpty() ? null : emps.get(0).getEmpName();
    }

    private Events findCompanyEventOrThrow(Long eventsId, UUID companyId){
        Events event = eventsRepository.findById(eventsId).orElseThrow(()-> new CustomException(ErrorCode.COMPANY_EVENT_NOT_FOUND));

        if (!event.getCompanyId().equals(companyId)){
            throw new CustomException(ErrorCode.EVENT_ACCESS_DENIED);
        }
        if(!Boolean.TRUE.equals(event.getIsAllEmployees())){
            throw new CustomException(ErrorCode.COMPANY_EVENT_NOT_COMPANY);
        }
        if(event.isDelete()){
            throw new CustomException(ErrorCode.EVENT_DELETED);
        }

        return event;
    }
}

package com.peoplecore.calendar.service;

import com.peoplecore.alarm.service.AlarmService;
import com.peoplecore.calendar.dtos.InterestCalendarResDto;
import com.peoplecore.calendar.dtos.InterestCalendarUpdateReqDto;
import com.peoplecore.calendar.dtos.ShareRequestCreateDto;
import com.peoplecore.calendar.dtos.ShareRequestResDto;
import com.peoplecore.calendar.entity.CalendarShareRequests;
import com.peoplecore.calendar.entity.InterestCalendars;
import com.peoplecore.calendar.enums.Permission;
import com.peoplecore.calendar.enums.ShareStatus;
import com.peoplecore.calendar.repository.CalendarShareRequestsRepository;
import com.peoplecore.calendar.repository.InterestCalendarsRepository;
import com.peoplecore.client.component.HrCacheService;
import com.peoplecore.client.dto.EmployeeSimpleResDto;
import com.peoplecore.event.AlarmEvent;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class InterestCalenderService {

    private final InterestCalendarsRepository interestCalendarsRepository;
    private final CalendarShareRequestsRepository calendarShareRequestsRepository;
    private final AlarmService alarmService;
    private final HrCacheService hrCacheService;

    @Autowired
    public InterestCalenderService(InterestCalendarsRepository interestCalendarsRepository, CalendarShareRequestsRepository calendarShareRequestsRepository, AlarmService alarmService, HrCacheService hrCacheService) {
        this.interestCalendarsRepository = interestCalendarsRepository;
        this.calendarShareRequestsRepository = calendarShareRequestsRepository;
        this.alarmService = alarmService;
        this.hrCacheService = hrCacheService;
    }


    //    1. 관심 캘린더 공유요청 + 알림
    @Transactional
    public void requestShare(UUID companyId, Long fromEmpId, ShareRequestCreateDto reqDto){

        Long targetEmpId = reqDto.getTargetEmpId();

        if (fromEmpId.equals(targetEmpId)){
            throw new CustomException(ErrorCode.SHARE_REQUEST_SELF);
        }

        // 중복 요청 방지
        if(calendarShareRequestsRepository.existsByCompanyIdAndFromEmpIdAndToEmpIdAndShareStatus(companyId, fromEmpId, targetEmpId, ShareStatus.PENDING)){
            throw new CustomException(ErrorCode.SHARE_REQUEST_DUPLICATE);
        }

        CalendarShareRequests shareRequest = CalendarShareRequests.builder()
                .fromEmpId(fromEmpId)
                .toEmpId(targetEmpId)
                .permission(Permission.READ_ONLY)
                .shareStatus(ShareStatus.PENDING)
                .requestedAt(LocalDateTime.now())
                .companyId(companyId)
                .build();

        calendarShareRequestsRepository.save(shareRequest);

        // 상대방에게 알람
        alarmService.createAndPush(AlarmEvent.builder()
                        .companyId(companyId)
                        .alarmType("Calendar")
                        .alarmTitle("캘린더 공유 요청")
                        .alarmContent("캘린더 공유 요청이 도착했습니다")
                        .alarmLink("/calendar/settings")
                        .alarmRefType("CALENDAR_SHARE")
                        .alarmRefId(shareRequest.getCalendarShareReqId())
                        .empIds(List.of(targetEmpId))
                        .build());
    }

//    2. 공유요청 응답 : 승인-> 관심캘린더 생성 + 알림, 거절-> 알림
    @Transactional
    public ShareRequestResDto shareRequestResponse(UUID companyId, Long empId, Long shareReqId, boolean accepted) {
        CalendarShareRequests shareRequest = findShareRequestOrThrow(shareReqId);
        validateShareRequestTarget(shareRequest, companyId, empId);

        if (accepted) {
            shareRequest.approve();

//        관심캘린더 생성
            InterestCalendars interestCalendar = InterestCalendars.builder()
                    .viewerEmpId(shareRequest.getFromEmpId())
                    .targetEmpId(shareRequest.getToEmpId())
                    .isVisible(true)
                    .shareDisplayColor("#4CAF50")
                    .sortOrder(1)
                    .createdAt(LocalDateTime.now())
                    .companyId(companyId)
                    .calendarShareRequest(shareRequest)
                    .build();
            interestCalendarsRepository.save(interestCalendar);
        } else {
            shareRequest.reject();
        }

//        요청자에게 알림
        String title = accepted ? "캘린더 공유 승인" : "캘린더 공유 거절";
        String content = accepted ? "캘린더 공유 요청이 승인되었습니다" : "캘린더 공유 요청이 거절되었습니다" ;

        alarmService.createAndPush(AlarmEvent.builder()
                .companyId(companyId)
                .alarmType("Calendar")
                .alarmTitle(title)
                .alarmContent(content)
                .alarmLink("/calendar/interest")
                .alarmRefType("CALENDAR_SHARE")
                .alarmRefId(shareReqId)
                .empIds(List.of(shareRequest.getFromEmpId()))
                .build());

//      사원 이름조회
        Map<Long, EmployeeSimpleResDto> empMap = getEmpMap(
                List.of(shareRequest.getFromEmpId(), shareRequest.getToEmpId()));

            return ShareRequestResDto.fromEntity(shareRequest, getEmpName(empMap, shareRequest.getFromEmpId()),getEmpName(empMap, shareRequest.getToEmpId()));
    }


//    3. 내가 등록한 관심캘린더 요청 목록
    public Page<ShareRequestResDto> getMyShareRequests(UUID companyId, Long empId, Pageable pageable){

        Page<CalendarShareRequests> page = calendarShareRequestsRepository.findByCompanyIdAndFromEmlIdOrderByRequestedAtDesc(companyId, empId, pageable);

//        페이지 내 empId 일괄 조회
        List<Long> empIds = page.getContent().stream()
                .flatMap(req-> Stream.of(req.getFromEmpId(), req.getToEmpId()))
                .distinct()
                .toList();
        Map<Long, EmployeeSimpleResDto> empMap = getEmpMap(empIds);

        return page.map(req -> ShareRequestResDto.fromEntity(req, getEmpName(empMap, req.getFromEmpId()), getEmpName(empMap, req.getToEmpId())));
    }


//    4. 내일정을 보고있는 동료(나한테 온 요청 목록)
    public Page<ShareRequestResDto> getReceivedRequests(UUID companyId, Long empId, Pageable pageable){
        Page<CalendarShareRequests> page = calendarShareRequestsRepository.findByCompanyIdAndToEmpIdOrderByRequestedAtDesc(companyId, empId,pageable);

        List<Long> empIds = page.getContent().stream()
                .flatMap(req -> Stream.of(req.getFromEmpId(), req.getToEmpId()))
                .distinct()
                .toList();
        Map<Long, EmployeeSimpleResDto> empMap = getEmpMap(empIds);

        return page.map(req -> ShareRequestResDto.fromEntity(req, getEmpName(empMap, req.getFromEmpId()),
                getEmpName(empMap, req.getToEmpId())));
    }

//    5. 관심캘린더 목록조회
    public List<InterestCalendarResDto> getInterestCalendars(UUID  companyId, Long empId){
        List<InterestCalendars> interestCalendars = interestCalendarsRepository.findByViewerEmpIndWithRequest(empId, companyId);

//        targetEmpId 일괄 조회
        List<Long> empIds = interestCalendars.stream()
                .map(InterestCalendars::getTargetEmpId)
                .distinct()
                .toList();
        Map<Long, EmployeeSimpleResDto> empMap = getEmpMap(empIds);

        return interestCalendars.stream()
                .map(ic -> InterestCalendarResDto.fromEntity(ic, getEmpName(empMap, ic.getTargetEmpId())))
                .toList();
    }


//    6. 관심캘린더 설정 변경(색상, 보이기, 순서)
    @Transactional
    public InterestCalendarResDto updateInterestCalendar(UUID companyId, Long empId, Long interestCalendarId, InterestCalendarUpdateReqDto reqDto){
        InterestCalendars interestCalendar = interestCalendarsRepository.findById(interestCalendarId).orElseThrow(()-> new CustomException(ErrorCode.INTEREST_CALENDAR_NOT_FOUND));

        if(interestCalendar.getViewerEmpId().equals(empId) || !interestCalendar.getCompanyId().equals(companyId)){
            throw new CustomException(ErrorCode.INTEREST_CALENDAR_OWNER_MISMATCH);
        }
        if(reqDto.getDisplayColor() != null){
            interestCalendar.updateColor(reqDto.getDisplayColor());
        }
        if(reqDto.getIsVisible() != null && !reqDto.getIsVisible().equals(interestCalendar.getIsVisible())){
            interestCalendar.toggleVisible();
        }
        if(reqDto.getSortOrder() != null){
            interestCalendar.updateSortOrder(reqDto.getSortOrder());
        }

        Map<Long, EmployeeSimpleResDto> empMap = getEmpMap(List.of(interestCalendar.getTargetEmpId()));
        return InterestCalendarResDto.fromEntity(interestCalendar, getEmpName(empMap, interestCalendar.getTargetEmpId()));
    }


//    7. 관심 캘린더 삭제
    @Transactional
    public void deleteInterestCalendar(UUID companyId, Long empId, Long interestCalendarId){
        InterestCalendars interestCalendar = interestCalendarsRepository.findById(interestCalendarId).orElseThrow(()-> new CustomException(ErrorCode.INTEREST_CALENDAR_NOT_FOUND));

        if (!interestCalendar.getViewerEmpId().equals(empId) || !interestCalendar.getCompanyId().equals(companyId)){
        }

        interestCalendar.getCalendarShareRequest().cancel();
        interestCalendarsRepository.delete(interestCalendar);
    }



    private CalendarShareRequests findShareRequestOrThrow(Long shareReqId){
        return calendarShareRequestsRepository.findById(shareReqId).orElseThrow(()-> new CustomException(ErrorCode.SHARE_REQUEST_NOT_FOUND) );
    }

    private void validateShareRequestTarget(CalendarShareRequests shareRequest, UUID companyId, Long empId){
        if (!shareRequest.getCompanyId().equals(companyId) || !shareRequest.getToEmpId().equals(empId)){
            throw new CustomException(ErrorCode.SHARE_REQUEST_ACCESS_DENIED);
        }
        if (shareRequest.getShareStatus() != ShareStatus.PENDING){
            throw new CustomException(ErrorCode.SHARE_REQUEST_ALREADY_PROCESSED);
        }
    }

//    empId 리스트 -> Map 으로 변환(HrCacheService 일괄조회)
    private Map<Long, EmployeeSimpleResDto> getEmpMap(List<Long> empIds){
        return hrCacheService.getEmployees(empIds).stream().collect(Collectors.toMap(EmployeeSimpleResDto::getEmpId, e-> e));
    }

//    Map에서 이름 꺼내기
    private String getEmpName(Map<Long, EmployeeSimpleResDto> empMap, Long empId){
        EmployeeSimpleResDto emp = empMap.get(empId);
        return emp != null ? emp.getEmpName() : null;
    }
}

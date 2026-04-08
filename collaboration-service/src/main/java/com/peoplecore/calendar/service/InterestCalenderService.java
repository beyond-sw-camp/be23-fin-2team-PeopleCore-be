package com.peoplecore.calendar.service;

import com.peoplecore.alarm.service.AlarmService;
import com.peoplecore.calendar.dtos.ShareRequestCreateDto;
import com.peoplecore.calendar.dtos.ShareRequestResDto;
import com.peoplecore.calendar.entity.CalendarShareRequests;
import com.peoplecore.calendar.entity.InterestCalendars;
import com.peoplecore.calendar.enums.Permission;
import com.peoplecore.calendar.enums.ShareStatus;
import com.peoplecore.calendar.repository.CalendarShareRequestsRepository;
import com.peoplecore.calendar.repository.InterestCalendarsRepository;
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
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InterestCalenderService {

    private final InterestCalendarsRepository interestCalendarsRepository;
    private final CalendarShareRequestsRepository calendarShareRequestsRepository;
    private final AlarmService alarmService;

    @Autowired
    public InterestCalenderService(InterestCalendarsRepository interestCalendarsRepository, CalendarShareRequestsRepository calendarShareRequestsRepository, AlarmService alarmService) {
        this.interestCalendarsRepository = interestCalendarsRepository;
        this.calendarShareRequestsRepository = calendarShareRequestsRepository;
        this.alarmService = alarmService;
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
    public ShareRequestResDto ShareRequestResponse(UUID companyId, Long empId, Long shareReqId, boolean accepted) {
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

            return ShareRequestResDto.fromEntity(shareRequest, null, null);
    }


//    3. 내가 등록한 관심캘린더 요청 목록
    @GetMapping
    public Page<ShareRequestResDto> getSentRequests(UUID companyId, Long empId, Pageable pageable){
        return calendarShareRequestsRepository.findByCompanyIdAndFromEmlIdOrderByRequestedAtDesc(companyId, empId, pageable).map(req -> ShareRequestResDto.fromEntity(req, null, null));

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
}

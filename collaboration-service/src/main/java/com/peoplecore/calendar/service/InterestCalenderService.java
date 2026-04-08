package com.peoplecore.calendar.service;

import com.peoplecore.calendar.dtos.ShareRequestCreateDto;
import com.peoplecore.calendar.entity.CalendarShareRequests;
import com.peoplecore.calendar.enums.Permission;
import com.peoplecore.calendar.enums.ShareStatus;
import com.peoplecore.calendar.repository.CalendarShareRequestsRepository;
import com.peoplecore.calendar.repository.InterestCalendarsRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InterestCalenderService {

    private final InterestCalendarsRepository interestCalendarsRepository;
    private final CalendarShareRequestsRepository calendarShareRequestsRepository;

    @Autowired
    public InterestCalenderService(InterestCalendarsRepository interestCalendarsRepository, CalendarShareRequestsRepository calendarShareRequestsRepository) {
        this.interestCalendarsRepository = interestCalendarsRepository;
        this.calendarShareRequestsRepository = calendarShareRequestsRepository;
    }


    //    관심 캘린더 공유요청 + 알림
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
    }
}

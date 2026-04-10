package com.peoplecore.calendar.service;

import com.peoplecore.calendar.dtos.MyCalendarCreateReqDto;
import com.peoplecore.calendar.dtos.MyCalendarResDto;
import com.peoplecore.calendar.dtos.MyCalendarUpdateReqDto;
import com.peoplecore.calendar.entity.MyCalendars;
import com.peoplecore.calendar.repository.MyCalendarsRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.print.Pageable;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MyCalendarService {

    private static final String DEFAULT_CALENDAR_NAME = "내 일정(기본)";
    private static final String DEFAULT_CALENDAR_COLOR = "#1A73E8";


    private final MyCalendarsRepository myCalendarsRepository;

    @Autowired
    public MyCalendarService(MyCalendarsRepository myCalendarsRepository) {
        this.myCalendarsRepository = myCalendarsRepository;
    }


    //    내 캘린더 목록조회 + 기본캘린더 없으면 자동 생성
    @Transactional
    public List<MyCalendarResDto> getMyCalendars(UUID companyId, Long empId) {
        List<MyCalendars> calendars = myCalendarsRepository.findByCompanyIdAndEmpIdOrderBySortOrderAsc(companyId, empId);

        if (calendars.isEmpty()) {
            MyCalendars defaultMyCal = MyCalendars.builder()
                    .empId(empId)
                    .calendarName(DEFAULT_CALENDAR_NAME)
                    .myDisplayColor(DEFAULT_CALENDAR_COLOR)
                    .isVisible(true)
                    .sortOrder(0)
                    .companyId(companyId)
                    .build();
            myCalendarsRepository.save(defaultMyCal);
            calendars = List.of(defaultMyCal);
        }

        return calendars.stream()
                .map(MyCalendarResDto::fromEntity)
                .toList();
    }


//    내 캘린더 추가
    @Transactional
    public MyCalendarResDto createMyCalendar(UUID companyId, Long empId, MyCalendarCreateReqDto reqDto){
        if(myCalendarsRepository.existsByCompanyIdAndEmpIdAndCalendarName(companyId, empId, reqDto.getCalendarName())){
            throw new CustomException(ErrorCode.CALENDAR_NAME_DUPLICATE);
        }

//        정렬순서
        List<MyCalendars> existing = myCalendarsRepository.findByCompanyIdAndEmpIdOrderBySortOrderAsc(companyId, empId);

        MyCalendars myCalendar = MyCalendars.builder()
                .empId(empId)
                .calendarName(reqDto.getCalendarName())
                .myDisplayColor(reqDto.getDisplayColor())
                .isVisible(true)
                .isDefault(false)
                .sortOrder(existing.size()+1)
                .companyId(companyId)
                .build();

        return  MyCalendarResDto.fromEntity(myCalendarsRepository.save(myCalendar));
    }


//     내 캘린더 수정
    @Transactional
    public MyCalendarResDto updateMyCalendar(UUID companyId, Long empId, Long calendarId, MyCalendarUpdateReqDto reqDto){

        MyCalendars myCalendar = findAndValidate(calendarId, companyId, empId);

//        기본캘린더는 이름변경 불가
        if(reqDto.getCalendarName() != null){
            if (myCalendar.isDefaultCalendar()){
                throw new CustomException(ErrorCode.DEFAULT_CALENDAR_CANNOT_RENAME);
            }
        }

        if(reqDto.getCalendarName() != null){
            myCalendar.updateName(reqDto.getCalendarName());
        }
        if (reqDto.getDisplayColor() != null){
            myCalendar.updateColor(reqDto.getDisplayColor());
        }
        if(reqDto.getIsVisible() != null){
            if (!reqDto.getIsVisible().equals(myCalendar.getIsVisible())){
                myCalendar.toggleVisible();
            }
        }
        if (reqDto.getSortOrder() != null){
            myCalendar.updateSortOrder(reqDto.getSortOrder());
        }

        return MyCalendarResDto.fromEntity(myCalendar);
    }

//    내캘린더 삭제
    @Transactional
    public void deleteMyCalendar(UUID companyId, Long empId, Long calendarId){

        MyCalendars myCalendar = findAndValidate(calendarId, companyId, empId);

        if(myCalendar.getIsDefault()){
            throw new CustomException(ErrorCode.DEFAULT_CALENDAR_CANNOT_DELETE);
        }

        myCalendarsRepository.delete(myCalendar);
    }



// 캘린더 유효 검증
    private MyCalendars findAndValidate(Long calendarId, UUID companyId, Long empId){
        MyCalendars myCalendar = myCalendarsRepository.findById(calendarId).orElseThrow(()-> new CustomException(ErrorCode.CALENDAR_NOT_FOUND));
        if (!myCalendar.getCompanyId().equals(companyId) || !myCalendar.getEmpId().equals(empId)){
            throw new CustomException(ErrorCode.CALENDAR_OWNER_MISMATCH);
        }
        return myCalendar;
    }
}

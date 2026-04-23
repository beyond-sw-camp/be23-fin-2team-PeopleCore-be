package com.peoplecore.calendar.service;

import com.peoplecore.calendar.dtos.MyCalendarCreateReqDto;
import com.peoplecore.calendar.dtos.MyCalendarResDto;
import com.peoplecore.calendar.dtos.MyCalendarUpdateReqDto;
import com.peoplecore.calendar.entity.MyCalendars;
import com.peoplecore.calendar.repository.MyCalendarsRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MyCalendarService {

    private static final String DEFAULT_CALENDAR_NAME = "내 일정(기본)";
    private static final String DEFAULT_CALENDAR_COLOR = "#1A73E8";
    /* 휴가 전용 캘린더 - 휴가 승인 시 자동으로 이벤트 생성됨. 이름/색 고정 */
    private static final String VACATION_CALENDAR_NAME = "휴가 캘린더";
    private static final String VACATION_CALENDAR_COLOR = "#E57373";
    private static final String LEAVE_CALENDAR_NAME = "휴가 일정";
    private static final String LEAVE_CALENDAR_COLOR = "#10B981";


    private final MyCalendarsRepository myCalendarsRepository;

    @Autowired
    public MyCalendarService(MyCalendarsRepository myCalendarsRepository) {
        this.myCalendarsRepository = myCalendarsRepository;
    }


    //    내 캘린더 목록조회
    @Transactional
    public List<MyCalendarResDto> getMyCalendars(UUID companyId, Long empId) {
        return ensureDefaultCalendars(companyId, empId).stream()
                .map(MyCalendarResDto::fromEntity)
                .toList();
    }

    // 기본캘린더(내일정,휴가일정) 없으면 자동생성하는 멱등 메서드
    @Transactional
    public List<MyCalendars> ensureDefaultCalendars(UUID companyId, Long empId) {
        List<MyCalendars> calendars = myCalendarsRepository
                .findByCompanyIdAndEmpIdOrderBySortOrderAsc(companyId, empId);

        boolean hasMy = calendars.stream()
                .anyMatch(c -> DEFAULT_CALENDAR_NAME.equals(c.getCalendarName()));
        boolean hasLeave = calendars.stream()
                .anyMatch(c -> LEAVE_CALENDAR_NAME.equals(c.getCalendarName()));

        if (!hasMy) {
            myCalendarsRepository.save(MyCalendars.builder()
                    .empId(empId)
                    .companyId(companyId)
                    .calendarName(DEFAULT_CALENDAR_NAME)
                    .myDisplayColor(DEFAULT_CALENDAR_COLOR)
                    .isVisible(true)
                    .isPublic(true)
                    .isDefault(true)
                    .sortOrder(0)
                    .build());
        }
        if (!hasLeave) {
            myCalendarsRepository.save(MyCalendars.builder()
                    .empId(empId)
                    .companyId(companyId)
                    .calendarName(LEAVE_CALENDAR_NAME)
                    .myDisplayColor(LEAVE_CALENDAR_COLOR)
                    .isVisible(true)
                    .isPublic(true)
                    .isDefault(true)
                    .sortOrder(1)
                    .build());
        }

        // 새로 만든 게 있으면 재조회(정렬 + 생성된 ID 포함)
        if (!hasMy || !hasLeave) {
            calendars = myCalendarsRepository
                    .findByCompanyIdAndEmpIdOrderBySortOrderAsc(companyId, empId);
        }
        return calendars;
    }

    /* 휴가 캘린더 조회 - 없으면 생성해 반환. 휴가 승인 시점에 호출됨 */
    @Transactional
    public MyCalendars ensureVacationCalendar(UUID companyId, Long empId) {
        return myCalendarsRepository
                .findByCompanyIdAndEmpIdAndCalendarName(companyId, empId, VACATION_CALENDAR_NAME)
                .orElseGet(() -> {
                    int nextOrder = myCalendarsRepository
                            .findByCompanyIdAndEmpIdOrderBySortOrderAsc(companyId, empId).size();
                    MyCalendars vacationCal = MyCalendars.builder()
                            .empId(empId)
                            .calendarName(VACATION_CALENDAR_NAME)
                            .myDisplayColor(VACATION_CALENDAR_COLOR)
                            .isVisible(true)
                            .isDefault(true)    // 시스템 예약 - 이름 변경/삭제 차단
                            .sortOrder(nextOrder)
                            .companyId(companyId)
                            .build();
                    return myCalendarsRepository.save(vacationCal);
                });
    }


    //    내 캘린더 추가
    @Transactional
    public MyCalendarResDto createMyCalendar(UUID companyId, Long empId, MyCalendarCreateReqDto reqDto) {
        if (myCalendarsRepository.existsByCompanyIdAndEmpIdAndCalendarName(companyId, empId, reqDto.getCalendarName())) {
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
                .sortOrder(existing.size() + 1)
                .isPublic(reqDto.getIsPublic() != null ? reqDto.getIsPublic() : Boolean.TRUE)
                .companyId(companyId)
                .build();

        return MyCalendarResDto.fromEntity(myCalendarsRepository.save(myCalendar));
    }


    //     내 캘린더 수정
    @Transactional
    public MyCalendarResDto updateMyCalendar(UUID companyId, Long empId, Long calendarId, MyCalendarUpdateReqDto reqDto) {

        MyCalendars myCalendar = findAndValidate(calendarId, companyId, empId);

//        기본캘린더는 이름변경 불가
        if (reqDto.getCalendarName() != null) {
            if (myCalendar.isDefaultCalendar()) {
                throw new CustomException(ErrorCode.DEFAULT_CALENDAR_CANNOT_RENAME);
            }
        }

        if (reqDto.getCalendarName() != null) {
            myCalendar.updateName(reqDto.getCalendarName());
        }
        if (reqDto.getDisplayColor() != null) {
            myCalendar.updateColor(reqDto.getDisplayColor());
        }
        if (reqDto.getIsVisible() != null) {
            if (!reqDto.getIsVisible().equals(myCalendar.getIsVisible())) {
                myCalendar.toggleVisible();
            }
        }
        if (reqDto.getSortOrder() != null) {
            myCalendar.updateSortOrder(reqDto.getSortOrder());
        }
        if (reqDto.getIsPublic() != null) {
            if (!reqDto.getIsPublic().equals(myCalendar.getIsPublic())) myCalendar.updatePublic();
        }

        return MyCalendarResDto.fromEntity(myCalendar);
    }

    //    내캘린더 삭제
    @Transactional
    public void deleteMyCalendar(UUID companyId, Long empId, Long calendarId) {

        MyCalendars myCalendar = findAndValidate(calendarId, companyId, empId);

        if (myCalendar.getIsDefault()) {
            throw new CustomException(ErrorCode.DEFAULT_CALENDAR_CANNOT_DELETE);
        }

        myCalendarsRepository.delete(myCalendar);
    }


    // 캘린더 유효 검증
    private MyCalendars findAndValidate(Long calendarId, UUID companyId, Long empId) {
        MyCalendars myCalendar = myCalendarsRepository.findById(calendarId).orElseThrow(() -> new CustomException(ErrorCode.CALENDAR_NOT_FOUND));
        if (!myCalendar.getCompanyId().equals(companyId) || !myCalendar.getEmpId().equals(empId)) {
            throw new CustomException(ErrorCode.CALENDAR_OWNER_MISMATCH);
        }
        return myCalendar;
    }
}

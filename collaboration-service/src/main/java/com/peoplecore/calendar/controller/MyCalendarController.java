package com.peoplecore.calendar.controller;

import com.peoplecore.calendar.dtos.MyCalendarCreateReqDto;
import com.peoplecore.calendar.dtos.MyCalendarResDto;
import com.peoplecore.calendar.dtos.MyCalendarUpdateReqDto;
import com.peoplecore.calendar.service.MyCalendarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/calendar/my")
public class MyCalendarController {

    private final MyCalendarService myCalendarService;

    @Autowired
    public MyCalendarController(MyCalendarService myCalendarService) {
        this.myCalendarService = myCalendarService;
    }


    //   내 캘린더 목록조회
    @GetMapping
    public ResponseEntity<List<MyCalendarResDto>> getMycCalendars(
            @RequestHeader("/X-User-Company") UUID componyId,
            @RequestHeader("X-User-Id") Long empId){
        return ResponseEntity.ok(myCalendarService.getMyCalendars(componyId, empId));
    }

//    내캘린더 추가
    @PostMapping
    public ResponseEntity<MyCalendarResDto> createMyCalendar(
            @RequestHeader("/X-User-Company") UUID componyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody MyCalendarCreateReqDto reqDto){
        return ResponseEntity.status(HttpStatus.CREATED).body(myCalendarService.createMyCalendar(componyId,empId,reqDto));
    }

//    내캘린더 수정
    @PatchMapping("/{calendarId}")
    public ResponseEntity<MyCalendarResDto> updateMyCalendar(
            @RequestHeader("/X-User-Company") UUID componyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long calendarId,
            @RequestBody MyCalendarUpdateReqDto reqDto){
        return ResponseEntity.ok(myCalendarService.updateMyCalendar(componyId, empId, calendarId, reqDto));
    }

//    내캘린더 삭제
    @DeleteMapping("/{calendarId}")
    public ResponseEntity<Void> deleteMyCalendar(
            @RequestHeader("/X-User-Company") UUID componyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long calendarId){
        myCalendarService.deleteMyCalendar(componyId, empId, calendarId);
        return ResponseEntity.noContent().build();
    }

}

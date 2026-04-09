package com.peoplecore.calendar.controller;

import com.peoplecore.calendar.dtos.EventCreateReqDto;
import com.peoplecore.calendar.dtos.EventResDto;
import com.peoplecore.calendar.dtos.EventUpdateReqDto;
import com.peoplecore.calendar.service.CalendarEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/calendar/events")
public class CalendarEventController {

    private final CalendarEventService calendarEventService;

    @Autowired
    public CalendarEventController(CalendarEventService calendarEventService) {
        this.calendarEventService = calendarEventService;
    }

//    일정등록
    @PostMapping
    public ResponseEntity<EventResDto> createEvent(
            @RequestHeader("X-User-Company")UUID componyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody EventCreateReqDto reqDto){
        return ResponseEntity.status(HttpStatus.CREATED).body(calendarEventService.createEvent(componyId,empId, reqDto));
    }

//    일정 수정
    @PutMapping("/{eventsId}")
    public ResponseEntity<EventResDto> updateEvent(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long eventsId,
            @RequestBody EventUpdateReqDto reqDto){
        return ResponseEntity.ok(calendarEventService.updateEvent(companyId, empId, eventsId, reqDto));
    }

//    일정 삭제
    @DeleteMapping("/{eventsId}")
    public ResponseEntity<Void> deleteEvent(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long eventsId){
        calendarEventService.deleteEvent(companyId, empId, eventsId);
        return ResponseEntity.noContent().build();
    }

//    일정 상세 조회
    @GetMapping("/{eventsId}")
    public ResponseEntity<EventResDto> getEvent(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long eventsId){
        return ResponseEntity.ok(calendarEventService.getEvent(companyId, eventsId));
    }

//    캘린더 뷰 일정 조회(기간별 조회)
    @GetMapping
    public ResponseEntity<List<EventResDto>> getEvents(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)LocalDateTime end){
        return ResponseEntity.ok(calendarEventService.getEventsForView(companyId, empId, start, end));
    }



}

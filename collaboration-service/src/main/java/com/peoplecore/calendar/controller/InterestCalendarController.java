package com.peoplecore.calendar.controller;

import com.peoplecore.calendar.dtos.InterestCalendarResDto;
import com.peoplecore.calendar.dtos.InterestCalendarUpdateReqDto;
import com.peoplecore.calendar.dtos.ShareRequestCreateDto;
import com.peoplecore.calendar.dtos.ShareRequestResDto;
import com.peoplecore.calendar.service.InterestCalenderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/calendar/interest")
public class InterestCalendarController {

    private final InterestCalenderService interestCalenderService;

    @Autowired
    public InterestCalendarController(InterestCalenderService interestCalenderService) {
        this.interestCalenderService = interestCalenderService;
    }


    //    1. 관심 캘린더 공유요청
    @PostMapping("/share-request")
    public ResponseEntity<Void> requestShare(
            @RequestHeader("X-User-Company") UUID componyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody ShareRequestCreateDto request) {

        interestCalenderService.requestShare(componyId, empId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();

    }

    //    2. 공유 요청 응답 (승인/거절)
    @PatchMapping("/share-request/{shareReqId}")
    public ResponseEntity<Void> approveShareRequest(
            @RequestHeader("X-User-Company") UUID componyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long shareReqId,
            @RequestParam boolean accepted) {
        interestCalenderService.shareRequestResponse(componyId, empId, shareReqId, accepted);
        return ResponseEntity.ok().build();
    }

//    3. 내가 등록한 관심캘린더 요청 목록
    @GetMapping("/share-request/sent")
    public ResponseEntity<Page<ShareRequestResDto>> getSentRequests(
            @RequestHeader("X-User-Company") UUID componyId,
            @RequestHeader("X-User-Id") Long empId,
            Pageable pageable){
        return ResponseEntity.ok(interestCalenderService.getMyShareRequests(componyId, empId, pageable));
    }

//    4. 내일정을 보고있는 동료(나한테 온 요청 목록)
    @GetMapping("/share-request/received")
    public ResponseEntity<Page<ShareRequestResDto>> getReceivedRequests(
            @RequestHeader("X-User-Company") UUID componyId,
            @RequestHeader("X-User-Id") Long empId,
            Pageable pageable){
        return ResponseEntity.ok(interestCalenderService.getReceivedRequests(componyId,empId,pageable));
    }

//    5. 관심캘린더 목록조회
    @GetMapping
    public ResponseEntity<List<InterestCalendarResDto>> getInterestCalenders(
            @RequestHeader("X-User-Company") UUID componyId,
            @RequestHeader("X-User-Id") Long empId){
        return ResponseEntity.ok(interestCalenderService.getInterestCalendars(componyId, empId));
    }

//    6. 관심캘린더 설정 변경(색상, 보이기, 순서)
    @PatchMapping("/{interestCalendarId}")
    public ResponseEntity<InterestCalendarResDto> updateInterestCalendar(
            @RequestHeader("X-User-Company") UUID componyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long interestCalendarId,
            @RequestBody InterestCalendarUpdateReqDto reqDto) {
        return ResponseEntity.ok(interestCalenderService.updateInterestCalendar(componyId, empId, interestCalendarId, reqDto));
    }

//    7. 관심 캘린더 삭제
    @DeleteMapping("/{interestCalendarId}")
    public ResponseEntity<Void> deleteInterestCalendar(
            @RequestHeader("X-User-Company") UUID componyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long interestCalendarId){
        interestCalenderService.deleteInterestCalendar(componyId, empId, interestCalendarId);
        return ResponseEntity.noContent().build();
    }

}

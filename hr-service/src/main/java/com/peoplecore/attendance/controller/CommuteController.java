package com.peoplecore.attendance.controller;

import com.peoplecore.attendance.dto.CheckInResDto;
import com.peoplecore.attendance.dto.CheckOutResDto;
import com.peoplecore.attendance.service.CommuteService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 출퇴근 체크인/아웃 API.
 * - 전 사원 공통 기능이라 @RoleRequired 미지정.
 * - Body 없음. IP 는 서버 추출.
 */
@RestController
@RequestMapping("/attendance")
public class CommuteController {

    private final CommuteService commuteService;

    @Autowired
    public CommuteController(CommuteService commuteService) {
        this.commuteService = commuteService;
    }

    /** 출근 체크인 - 201 / 409 COMMUTE_ALREADY_CHECKED_IN / 409 EMPLOYEE_WORK_GROUP_NOT_ASSIGNED */
    @PostMapping("/check-in")
    public ResponseEntity<CheckInResDto> checkIn(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commuteService.checkIn(companyId, empId, request));
    }

    /** 퇴근 체크아웃 - 200 / 404 COMMUTE_NOT_CHECKED_IN / 409 COMMUTE_ALREADY_CHECKED_OUT */
    @PostMapping("/check-out")
    public ResponseEntity<CheckOutResDto> checkOut(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            HttpServletRequest request) {
        return ResponseEntity.ok(commuteService.checkOut(companyId, empId, request));
    }
}
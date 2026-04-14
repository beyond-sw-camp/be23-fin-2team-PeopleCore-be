package com.peoplecore.attendance.controller;

import com.peoplecore.attendance.dto.OvertimeRemainingResDto;
import com.peoplecore.attendance.dto.OvertimeWeekHistoryResDto;
import com.peoplecore.attendance.service.OvertimeRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 사원용 초과근무 조회 컨트롤러.
 * 신청(insert) 자체는 더 이상 여기로 오지 않음 — 결재문서 상신 성공 시 collab→hr Kafka 로 자동 insert.
 */
@RestController
@RequestMapping("/attendance/overtime")
public class OvertimeRequestController {

    private final OvertimeRequestService overtimeRequestService;

    @Autowired
    public OvertimeRequestController(OvertimeRequestService overtimeRequestService) {
        this.overtimeRequestService = overtimeRequestService;
    }

    /** 모달 진입 — 잔여 초과근로시간 조회 (weekStart 는 월요일 권장, 서버 정규화) */
    @GetMapping("/remaining")
    public ResponseEntity<OvertimeRemainingResDto> getRemaining(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart
    ) {
        return ResponseEntity.ok(overtimeRequestService.getRemaining(companyId, empId, weekStart));
    }

    /** 모달 하단 이력 — 이번주 본인 신청건 */
    @GetMapping("/week")
    public ResponseEntity<OvertimeWeekHistoryResDto> getWeek(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart
    ) {
        return ResponseEntity.ok(overtimeRequestService.getWeekHistory(companyId, empId, weekStart));
    }
}

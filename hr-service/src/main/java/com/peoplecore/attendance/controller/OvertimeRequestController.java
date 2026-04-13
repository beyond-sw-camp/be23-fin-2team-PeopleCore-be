package com.peoplecore.attendance.controller;

import com.peoplecore.attendance.dto.OvertimeRemainingResDto;
import com.peoplecore.attendance.dto.OvertimeSubmitRequest;
import com.peoplecore.attendance.service.OvertimeRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** 사원용 초과근무 신청 컨트롤러 — 자신의 신청만 가능 (헤더 X-User-Id 로 분리) */
@RestController
@RequestMapping("/attendance/overtime")
public class OvertimeRequestController {

    private final OvertimeRequestService overtimeRequestService;

    @Autowired
    public OvertimeRequestController(OvertimeRequestService overtimeRequestService) {
        this.overtimeRequestService = overtimeRequestService;
    }

    /** 모달 진입 — 잔여 초과근로시간 조회. weekStart 는 주 시작일(월요일 권장) */
    @GetMapping("/remaining")
    public ResponseEntity<OvertimeRemainingResDto> getRemaining(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart
    ) {
        return ResponseEntity.ok(
                overtimeRequestService.getRemaining(companyId, empId, weekStart));
    }

    /** "확인" 클릭 → otId 반환. 프론트가 결재문서 작성 페이지로 prefill 라우팅 */
    @PostMapping
    public ResponseEntity<Long> submit(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody OvertimeSubmitRequest request
    ) {
        Long otId = overtimeRequestService.submit(companyId, empId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(otId);
    }
}

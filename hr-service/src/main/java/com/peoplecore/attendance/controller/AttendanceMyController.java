package com.peoplecore.attendance.controller;

import com.peoplecore.attendance.dto.AttendanceMyMonthlySummaryResDto;
import com.peoplecore.attendance.dto.AttendanceMyWeeklySummaryResDto;
import com.peoplecore.attendance.service.AttendanceMySummaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * 사원 개인 근태 API.
 */
@RestController
@RequestMapping("/attendance/my")
public class AttendanceMyController {

    private final AttendanceMySummaryService attendanceMySummaryService;

    @Autowired
    public AttendanceMyController(AttendanceMySummaryService attendanceMySummaryService) {
        this.attendanceMySummaryService = attendanceMySummaryService;
    }

    /**
     * 주간 근태 요약.
     * 응답 3블록: today (오늘 출퇴근) / workGroup (근무그룹 + 회사정책) / weekly (주간 집계).
     */
    @GetMapping("/weekly-summary")
    public ResponseEntity<AttendanceMyWeeklySummaryResDto> getWeeklySummary(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(attendanceMySummaryService.getWeeklySummary(companyId, empId, date));
    }

    /**
     * 월간 근태 요약 — 지각/인증 초과근무 일자별 상세 + 헤더 카운트.
     * yearMonth 미지정 시 현재 월 기준.
     */
    @GetMapping("/monthly-summary")
    public ResponseEntity<AttendanceMyMonthlySummaryResDto> getMonthlySummary(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam(value = "yearMonth", required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth yearMonth) {
        return ResponseEntity.ok(attendanceMySummaryService.getMonthlySummary(companyId, empId, yearMonth));
    }
}
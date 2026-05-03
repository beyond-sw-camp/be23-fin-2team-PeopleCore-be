package com.peoplecore.pay.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.pay.dtos.*;
import com.peoplecore.pay.service.MySalaryService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pay/my")
@RoleRequired({"EMPLOYEE","HR_SUPER_ADMIN","HR_ADMIN"})

public class MySalaryController {

    private final MySalaryService mySalaryService;

    public MySalaryController(MySalaryService mySalaryService) {
        this.mySalaryService = mySalaryService;
    }

    /** 내 급여 정보 조회 */
    @GetMapping("/info")
    public ResponseEntity<MySalaryInfoResDto> getMyInfo(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(mySalaryService.getMySalaryInfo(companyId, empId));
    }


    /** 연도별 급여명세서 목록 */
    @GetMapping("/stubs")
    public ResponseEntity<List<PayStubListResDto>> getStubList(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam String year) {
        return ResponseEntity.ok(mySalaryService.getPayStubList(companyId, empId, year));
    }

    /** 급여명세서 상세 */
    @GetMapping("/stubs/{stubId}")
    public ResponseEntity<PayStubDetailResDto> getStubDetail(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long stubId) {
        return ResponseEntity.ok(mySalaryService.getPayStubDetail(companyId, empId, stubId));
    }

    /** 퇴직연금 정보 */
    @GetMapping("/pension")
    public ResponseEntity<PensionInfoResDto> getPension(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(mySalaryService.getPensionInfo(companyId, empId));
    }

    /** 내 예상 퇴직금 (근속기준 추계액) */
    @GetMapping("/severance-estimate")
    public ResponseEntity<MySeveranceEstimateResDto> getMySeveranceEstimate(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate) {
        return ResponseEntity.ok(
                mySalaryService.getMySeveranceEstimate(companyId, empId, baseDate)
        );
    }


}

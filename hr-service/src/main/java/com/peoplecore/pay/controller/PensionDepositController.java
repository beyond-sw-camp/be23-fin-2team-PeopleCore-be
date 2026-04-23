package com.peoplecore.pay.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.pay.dtos.*;
import com.peoplecore.pay.enums.DepStatus;
import com.peoplecore.pay.service.PensionDepositService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pay/admin/pension-deposits")
@RoleRequired({"HR_SUPER_ADMIN","HR_ADMIN"})
public class PensionDepositController {
// 퇴직연금 적립내역 조회

    private final PensionDepositService pensionDepositService;
    @Autowired
    public PensionDepositController(PensionDepositService pensionDepositService) {
        this.pensionDepositService = pensionDepositService;
    }

    // 1. 목록조회
    @GetMapping
    public ResponseEntity<PensionDepositSummaryResDto> getList(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam(required = false) String fromYm,
            @RequestParam(required = false) String toYm,
            @RequestParam(required = false) Long empId,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) DepStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                pensionDepositService.getDepositList(companyId, fromYm, toYm, empId, deptId, status, pageable));
    }

    // 2. 사원별 이력조회
    @GetMapping("/employee/{empId}")
    public ResponseEntity<PensionDepositEmployeeResDto> getEmployeeDeposits(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long empId,
            @RequestParam(required = false) String fromYm,
            @RequestParam(required = false) String toYm) {
        return ResponseEntity.ok(
                pensionDepositService.getEmployeeDeposits(companyId, empId, fromYm, toYm));
    }

    // 3. 수동 적립 등록
    @PostMapping
    public ResponseEntity<PensionDepositResDto> createManual(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long adminEmpId,
            @RequestBody @Valid PensionDepositCreateReqDto reqDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                pensionDepositService.createManualDeposit(companyId, adminEmpId, reqDto));
    }

    // 4. 적립 취소
    @DeleteMapping("/{depId}")
    public ResponseEntity<Void> cancel(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long adminEmpId,
            @PathVariable Long depId,
            @RequestParam(required = false) String reason) {
        pensionDepositService.cancelDeposit(companyId, adminEmpId, depId, reason);
        return ResponseEntity.noContent().build();
    }

    // 5. 월별 요약
    @GetMapping("/monthly-summary")
    public ResponseEntity<List<MonthlyDepositSummaryDto>> monthlySummary(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(pensionDepositService.getMonthlySummary(companyId, year));
    }


    // 6. 사원별 집계 (화면 메인 테이블용(리스트) - 사원당 1행으로 묶음)
    @GetMapping("/by-employee")
    public ResponseEntity<PensionDepositByEmployeeSummaryResDto> getByEmployee(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam(required = false) String fromYm,
            @RequestParam(required = false) String toYm,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) DepStatus status) {
        return ResponseEntity.ok(
                pensionDepositService.getDepositByEmployee(companyId, fromYm, toYm, search, deptId, status));
    }

}


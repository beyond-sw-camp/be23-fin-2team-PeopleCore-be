package com.peoplecore.pay.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.pay.dtos.LeaveAllowanceSummaryResDto;
import com.peoplecore.pay.dtos.LeavePolicyTypeResDto;
import com.peoplecore.pay.enums.AllowanceType;
import com.peoplecore.pay.service.LeaveAllowanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pay/admin/leave-allowance")
@RoleRequired({"HR_SUPER_ADMIN","HR_ADMIN"})
public class LeaveAllowanceController {

    private final LeaveAllowanceService leaveAllowanceService;

    public LeaveAllowanceController(LeaveAllowanceService leaveAllowanceService) {
        this.leaveAllowanceService = leaveAllowanceService;
    }


//    연말 미사용 연차 산정 목록
    @GetMapping("/year-end")
    public ResponseEntity<LeaveAllowanceSummaryResDto> getFiscalYearList(
            @RequestHeader("X-User-Company")UUID companyId,
            @RequestParam Integer year){
        return ResponseEntity.ok(leaveAllowanceService.getFiscalYearList(companyId, year));
    }

//    퇴직자 연차 정산 목록
    @GetMapping("/resigned")
    public ResponseEntity<LeaveAllowanceSummaryResDto> getResignedList(
            @RequestHeader("X-User-Company")UUID companyId,
            @RequestParam Integer year){
        return ResponseEntity.ok(
            leaveAllowanceService.getResignedList(companyId, year));
    }

//        수당 산정(선택한 사원)
    @PostMapping("/calculate")
    public ResponseEntity<Void> calculate(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam Integer year,
            @RequestParam AllowanceType type,
            @RequestBody List<Long> empIds) {
        leaveAllowanceService.calculate(companyId, year, type, empIds);
        return ResponseEntity.ok().build();
    }

//    급여대장 반영(선택된 산정건)
    @PostMapping("/apply-to-payroll")
    public ResponseEntity<Void> applyToPayroll(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestBody List<Long> allowanceIds) {
        leaveAllowanceService.applyToPayroll(companyId, allowanceIds);
        return ResponseEntity.ok().build();
    }

//    회사 연차정책 타입 조회(프론트 탭 분기용)
    @GetMapping("/policy-type")
    public ResponseEntity<LeavePolicyTypeResDto> getPolicyType(
            @RequestHeader("X-User-Company") UUID companyId){
        return ResponseEntity.ok(leaveAllowanceService.getPolicyType(companyId));
    }

//    입사일 기준 연차
}

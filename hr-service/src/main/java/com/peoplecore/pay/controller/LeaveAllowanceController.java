package com.peoplecore.pay.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.pay.dtos.ApplyResultDto;
import com.peoplecore.pay.dtos.LeaveAllowanceSummaryResDto;
import com.peoplecore.pay.dtos.LeavePolicyTypeResDto;
import com.peoplecore.pay.enums.AllowanceStatus;
import com.peoplecore.pay.enums.AllowanceType;
import com.peoplecore.pay.repository.LeaveAllowanceRepository;
import com.peoplecore.pay.service.LeaveAllowanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pay/admin/leave-allowance")
@RoleRequired({"HR_SUPER_ADMIN","HR_ADMIN"})
public class LeaveAllowanceController {

    private final LeaveAllowanceService leaveAllowanceService;
    private final LeaveAllowanceRepository leaveAllowanceRepository;

    @Autowired
    public LeaveAllowanceController(LeaveAllowanceService leaveAllowanceService, LeaveAllowanceRepository leaveAllowanceRepository) {
        this.leaveAllowanceService = leaveAllowanceService;
        this.leaveAllowanceRepository = leaveAllowanceRepository;
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

    //    입사기념일 도래 사원 연차수당 목록 (해당 월 yearMonth='YYYY-MM')
    @GetMapping("/anniversary")
    public ResponseEntity<LeaveAllowanceSummaryResDto> getAnniversaryList(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam String yearMonth){
        return ResponseEntity.ok(
                leaveAllowanceService.getAnniversaryList(companyId, yearMonth));
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
    public ResponseEntity<ApplyResultDto> applyToPayroll(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestBody List<Long> allowanceIds) {
        return ResponseEntity.ok(leaveAllowanceService.applyToPayroll(companyId, allowanceIds));
    }

//    검토 대기 카운트 ("검토 대기 N명" 카드 표시용)
    @GetMapping("/pending-review/count")
    public ResponseEntity<Long> countPendingReview(
            @RequestHeader("X-User-Company") UUID companyId) {
        long count = leaveAllowanceRepository
                .countByCompany_CompanyIdAndStatusAndAppliedMonthIsNull(
                        companyId, AllowanceStatus.CALCULATED);
        return ResponseEntity.ok(count);
    }

//    회사 연차정책 타입 조회(프론트 탭 분기용)
    @GetMapping("/policy-type")
    public ResponseEntity<LeavePolicyTypeResDto> getPolicyType(
            @RequestHeader("X-User-Company") UUID companyId){
        return ResponseEntity.ok(leaveAllowanceService.getPolicyType(companyId));
    }

//    입사일 기준 연차
}

package com.peoplecore.vacation.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.vacation.dto.VacationAdjustmentHistoryResponseDto;
import com.peoplecore.vacation.dto.VacationBalanceResponse;
import com.peoplecore.vacation.dto.VacationGrantRequest;
import com.peoplecore.vacation.service.VacationBalanceService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/* 휴가 잔여 Controller - 사원 조회 + 관리자 부여 */
@RestController
@RequestMapping("/vacation/balances")
public class VacationBalanceController {

    private final VacationBalanceService vacationBalanceService;

    @Autowired
    public VacationBalanceController(VacationBalanceService vacationBalanceService) {
        this.vacationBalanceService = vacationBalanceService;
    }

    /* 내 잔여 목록 - year 생략 시 올해. 로그인 사원 누구나 */
    @GetMapping("/me")
    public ResponseEntity<List<VacationBalanceResponse>> listMine(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(vacationBalanceService.listMine(companyId, empId, year));
    }

    /* 관리자 일괄 부여 */
    /* @Valid: VacationGrantRequest 검증 (@NotNull/@NotEmpty/@Positive). 실패 시 400 BAD_REQUEST */
    /* @RoleRequired: HR_SUPER_ADMIN / HR_ADMIN 만 허용. 미일치 시 403 */
    /* managerId 는 X-User-Id 헤더에서 추출 → VacationLedger.managerId 에 저장되어 감사 추적 */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @PostMapping("/grant")
    public ResponseEntity<Void> grant(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long managerId,
            @Valid @RequestBody VacationGrantRequest request) {
        vacationBalanceService.grantBulk(companyId, managerId, request);
        return ResponseEntity.ok().build();
    }

    /* 특정 사원의 관리자 수동 조정 이력 - 스크롤형 Slice */
    /* MANUAL_GRANT / MANUAL_USED 만. year / typeId 동적 필터 */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping("/{empId}/adjustments")
    public ResponseEntity<Slice<VacationAdjustmentHistoryResponseDto>> listAdjustments(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long empId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long typeId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(vacationBalanceService.listAdjustmentHistory(
                companyId, empId, year, typeId, pageable));
    }
}
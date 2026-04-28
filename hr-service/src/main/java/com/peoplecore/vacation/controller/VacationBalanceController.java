package com.peoplecore.vacation.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.vacation.dto.MyVacationStatusResponseDto;
import com.peoplecore.vacation.dto.VacationAdjustmentHistoryResponseDto;
import com.peoplecore.vacation.dto.VacationBalanceResponse;
import com.peoplecore.vacation.dto.VacationManualGrantRequest;
import com.peoplecore.vacation.service.VacationBalanceService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
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

    /* 관리자 일괄 부여 */
    /* @Valid: VacationManualGrantRequest 검증 (@NotNull/@NotEmpty/@Positive). 실패 시 400 BAD_REQUEST */
    /* @RoleRequired: HR_SUPER_ADMIN / HR_ADMIN 만 허용. 미일치 시 403 */
    /* managerId 는 X-User-Id 헤더에서 추출 → VacationLedger.managerId 에 저장되어 감사 추적 */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @PostMapping("/grant")
    public ResponseEntity<Void> grant(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long managerId,
            @Valid @RequestBody VacationManualGrantRequest request) {
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

    /* 내 휴가 현황 조회 - 휴가현황 페이지 */
    /* year 필수 (프론트 전송). 예: GET /vacation/balances/me/status?year=2026 */
    /* 응답: 연차 카드 + 기타 휴가 + 예정/지난 신청 */
    /* 본인 것만 조회 (empId 헤더 기준 스코프) → @RoleRequired 불필요 */
    @GetMapping("/me/status")
    public ResponseEntity<MyVacationStatusResponseDto> getMyStatus(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam int year) {
        return ResponseEntity.ok(vacationBalanceService.getMyVacationStatus(companyId, empId, year));
    }

    /* 내 예정 휴가 페이지 - 휴가현황 페이지 upcoming 탭 */
    /* 정렬은 서버 고정(startAt asc) → Pageable.sort 는 무시 */
    /* 예: GET /vacation/balances/me/requests/upcoming?year=2026&page=0&size=10 */
    @GetMapping("/me/requests/upcoming")
    public ResponseEntity<Page<MyVacationStatusResponseDto.RequestItem>> listMyUpcoming(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam int year,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(vacationBalanceService.listMyUpcoming(companyId, empId, year, pageable));
    }

    /* 내 지난 휴가 페이지 - 휴가현황 페이지 past 탭 */
    /* 정렬은 서버 고정(endAt desc) → Pageable.sort 는 무시 */
    /* 예: GET /vacation/balances/me/requests/past?year=2026&page=0&size=10 */
    @GetMapping("/me/requests/past")
    public ResponseEntity<Page<MyVacationStatusResponseDto.RequestItem>> listMyPast(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam int year,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(vacationBalanceService.listMyPast(companyId, empId, year, pageable));
    }
}
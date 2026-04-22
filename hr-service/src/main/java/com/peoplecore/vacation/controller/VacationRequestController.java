package com.peoplecore.vacation.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.vacation.dto.CancelRequest;
import com.peoplecore.vacation.dto.VacationAdminPeriodResponseDto;
import com.peoplecore.vacation.dto.VacationRequestResponse;
import com.peoplecore.vacation.entity.RequestStatus;
import com.peoplecore.vacation.service.VacationRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/* 휴가 신청 Controller - 사원/관리자 조회 + 취소 */
@RestController
@RequestMapping("/vacation/requests")
public class VacationRequestController {

    private final VacationRequestService vacationRequestService;

    @Autowired
    public VacationRequestController(VacationRequestService vacationRequestService) {
        this.vacationRequestService = vacationRequestService;
    }

    /* 관리자 상태별 조회 (페이지) - status = PENDING/APPROVED/REJECTED/CANCELED */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping("/admin")
    public ResponseEntity<Page<VacationRequestResponse>> listForAdmin(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam RequestStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(vacationRequestService.listForAdmin(companyId, status, pageable));
    }

    /* 전사 휴가 관리 - 기간 + 상태 복수 필터 페이지 조회 */
    /* 예: GET /vacation/requests/admin/period?startDate=2026-04-01&endDate=2026-04-05&statuses=PENDING,APPROVED&page=0&size=20 */
    /* statuses 생략 시 전체 상태. 응답: 사원명/부서/유형/사용옵션/기간/일수/상태 */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping("/admin/period")
    public ResponseEntity<Page<VacationAdminPeriodResponseDto>> listForAdminByPeriod(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) List<RequestStatus> statuses,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                vacationRequestService.listForAdminByPeriod(companyId, startDate, endDate, statuses, pageable));
    }

    /* 관리자 직권 취소 - 상태 전이 규칙 우회 (applyByAdmin) */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @PostMapping("/admin/{requestId}/cancel")
    public ResponseEntity<Void> cancelAsAdmin(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long managerId,
            @PathVariable Long requestId,
            @RequestBody CancelRequest body) {
        vacationRequestService.cancelByAdmin(companyId, managerId, requestId, body.getReason());
        return ResponseEntity.noContent().build();
    }
}
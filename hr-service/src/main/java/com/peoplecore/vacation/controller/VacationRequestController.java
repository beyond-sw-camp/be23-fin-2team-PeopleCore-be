package com.peoplecore.vacation.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.vacation.dto.CancelRequest;
import com.peoplecore.vacation.dto.VacationRequestResponse;
import com.peoplecore.vacation.entity.RequestStatus;
import com.peoplecore.vacation.service.VacationRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    /* 내 신청 이력 (페이지) - createdAt 내림차순 기본 */
    @GetMapping("/me")
    public ResponseEntity<Page<VacationRequestResponse>> listMine(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(vacationRequestService.listMine(companyId, empId, pageable));
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

    /* 사원 셀프 취소 - PENDING/APPROVED 에서만 가능 (정상 전이) */
    /* 본인 request 검증은 Service 에서. body 는 optional (reason 없으면 null) */
    @PostMapping("/{requestId}/cancel")
    public ResponseEntity<Void> cancelMine(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long requestId,
            @RequestBody(required = false) CancelRequest body) {
        String reason = body != null ? body.getReason() : null;
        vacationRequestService.cancelByEmployee(companyId, empId, requestId, reason);
        return ResponseEntity.noContent().build();
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
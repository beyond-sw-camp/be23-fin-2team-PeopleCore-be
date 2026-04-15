package com.peoplecore.attendance.controller;

import com.peoplecore.attendance.dto.OvertimeRequestAdminRowResDto;
import com.peoplecore.attendance.dto.PagedResDto;
import com.peoplecore.attendance.entity.OtStatus;
import com.peoplecore.attendance.service.OvertimeRequestAdminService;
import com.peoplecore.auth.RoleRequired;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/* 관리자 — 초과근무 신청 관리 화면 (탭 4개)
 * GET /attendance/admin/overtime-requests           (전체)
 * GET /attendance/admin/overtime-requests/pending   (승인대기)
 * GET /attendance/admin/overtime-requests/approved  (승인완료)
 * GET /attendance/admin/overtime-requests/rejected  (반려)
 *
 * 권한: HR_SUPER_ADMIN, HR_ADMIN
 * 페이징: page(0-based), size (default 0/10)
 * 4개 엔드포인트는 OvertimeRequestAdminService.getRequests 호출 시 status 만 다름 */
@RestController
@RequestMapping("/attendance/admin/overtime-requests")
public class OvertimeRequestAdminController {

    private final OvertimeRequestAdminService overtimeRequestAdminService;

    @Autowired
    public OvertimeRequestAdminController(OvertimeRequestAdminService overtimeRequestAdminService) {
        this.overtimeRequestAdminService = overtimeRequestAdminService;
    }

    /* 전체 탭 — 상태 무관 모든 신청 (status null) */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping
    public ResponseEntity<PagedResDto<OvertimeRequestAdminRowResDto>> getAll(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(overtimeRequestAdminService.getRequests(companyId, null, page, size));
    }

    /* 승인대기 탭 — PENDING */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping("/pending")
    public ResponseEntity<PagedResDto<OvertimeRequestAdminRowResDto>> getPending(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(overtimeRequestAdminService.getRequests(companyId, OtStatus.PENDING, page, size));
    }

    /* 승인완료 탭 — APPROVED */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping("/approved")
    public ResponseEntity<PagedResDto<OvertimeRequestAdminRowResDto>> getApproved(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(overtimeRequestAdminService.getRequests(companyId, OtStatus.APPROVED, page, size));
    }

    /* 반려 탭 — REJECTED */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping("/rejected")
    public ResponseEntity<PagedResDto<OvertimeRequestAdminRowResDto>> getRejected(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(overtimeRequestAdminService.getRequests(companyId, OtStatus.REJECTED, page, size));
    }
}

package com.peoplecore.vacation.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.vacation.dto.CancelRequest;
import com.peoplecore.vacation.dto.VacationGrantRequestResponse;
import com.peoplecore.vacation.dto.VacationGrantableTypeResponse;
import com.peoplecore.vacation.service.VacationGrantRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/* 휴가 부여 신청 Controller - 사원/관리자 조회 + 취소 */
/* 신청 자체는 collab 결재 기안 → Kafka grantDocCreated → Consumer → Service.createFromApproval 경유 */
@RestController
@RequestMapping("/vacation/grant-requests")
public class VacationGrantRequestController {

    private final VacationGrantRequestService vacationGrantRequestService;

    @Autowired
    public VacationGrantRequestController(VacationGrantRequestService vacationGrantRequestService) {
        this.vacationGrantRequestService = vacationGrantRequestService;
    }

    /* 내 부여 신청 이력 (페이지) - createdAt 내림차순 */
    @GetMapping("/me")
    public ResponseEntity<Page<VacationGrantRequestResponse>> listMine(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(vacationGrantRequestService.listMine(companyId, empId, pageable));
    }

    /* 부여 신청 가능한 법정 휴가 유형 + 현재 잔여/한도/추가 신청 가능 일수 - 모달 드롭다운용 */
    /* EVENT_BASED 유형 중 본인 성별에 허용된 것만 반환. Balance 없으면 0 으로 초기화 */
    @GetMapping("/grantable-types")
    public ResponseEntity<List<VacationGrantableTypeResponse>> listGrantableTypes(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(vacationGrantRequestService.listGrantableTypes(companyId, empId));
    }

    /* 사원 셀프 취소 - PENDING/APPROVED 모두. APPROVED 는 사용분 검증 후 rollbackAccrual */
    @PostMapping("/{requestId}/cancel")
    public ResponseEntity<Void> cancelMine(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long requestId,
            @RequestBody(required = false) CancelRequest body) {
        String reason = body != null ? body.getReason() : null;
        vacationGrantRequestService.cancelByEmployee(companyId, empId, requestId, reason);
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
        vacationGrantRequestService.cancelByAdmin(companyId, managerId, requestId, body.getReason());
        return ResponseEntity.noContent().build();
    }
}

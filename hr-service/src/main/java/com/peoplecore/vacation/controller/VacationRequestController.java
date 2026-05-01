package com.peoplecore.vacation.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.vacation.dto.MyVacationTypeResponseDto;
import com.peoplecore.vacation.dto.VacationAdminPeriodPageResponse;
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

/* 휴가 신청 Controller - 사원/관리자 조회 전용 (취소는 collab 결재 문서 회수 경로로 일원화) */
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

    /* 전사 휴가 관리 - 기간 + 상태 필터, 건별 페이지 + 메타 */
    /* 예: GET /vacation/requests/admin/period?startDate=2026-04-01&endDate=2026-04-30&page=0&size=20 */
    /* statuses 생략 시 서비스에서 APPROVED 강제. 응답: 건별 content + uniqueEmployeeCount + totalUseDays */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping("/admin/period")
    public ResponseEntity<VacationAdminPeriodPageResponse> listForAdminByPeriod(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) List<RequestStatus> statuses,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                vacationRequestService.listForAdminByPeriod(companyId, startDate, endDate, statuses, pageable));
    }

    /* 본인 보유 휴가 유형 - 휴가 사용 신청 모달 드롭다운 */
    /* Balance 존재 + isActive 유형만. 잔여량/선사용 가능 판단은 프론트 */
    @GetMapping("/my-vacation-types")
    public ResponseEntity<List<MyVacationTypeResponseDto>> listMyVacationTypes(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(vacationRequestService.listMyVacationTypes(companyId, empId));
    }

    /* 내 휴가 신청 이력 (페이지) - createdAt 내림차순 */
    /* 응답: 신청일/유형/기간/일수/사유/상태(+반려사유)/처리일/requestId(취소용) */
    @GetMapping("/me")
    public ResponseEntity<Page<VacationRequestResponse>> listMine(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(vacationRequestService.listMine(companyId, empId, pageable));
    }
}
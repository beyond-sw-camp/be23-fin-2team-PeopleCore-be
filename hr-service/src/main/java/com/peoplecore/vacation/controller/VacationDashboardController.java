package com.peoplecore.vacation.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.vacation.dto.DepartmentMemberVacationResponseDto;
import com.peoplecore.vacation.dto.DepartmentVacationSummaryResponseDto;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.service.VacationDashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/* 전사 휴가 관리 대시보드 Controller - 관리자 전용 */
/* year 는 두 엔드포인트 모두 필수 (연도 기준 집계라 기본값 부여 지양) */
@RestController
@RequestMapping("/vacation/dashboard")
public class VacationDashboardController {

    private final VacationDashboardService vacationDashboardService;

    @Autowired
    public VacationDashboardController(VacationDashboardService vacationDashboardService) {
        this.vacationDashboardService = vacationDashboardService;
    }

    /* 부서별 요약 카드 목록 - 상단 카드 영역 */
    /* 예: GET /vacation/dashboard/departments?year=2026&lowUsageThreshold=30 */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping("/departments")
    public ResponseEntity<List<DepartmentVacationSummaryResponseDto>> getDepartmentSummaries(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam Integer year,
            @RequestParam(required = false) Integer lowUsageThreshold) {
        return ResponseEntity.ok(
                vacationDashboardService.getDepartmentSummaries(companyId, year, lowUsageThreshold));
    }

    /* 부서 선택 후 사원 상세 페이지 - 하단 테이블 영역 */
    /* 예: GET /vacation/dashboard/departments/5/members?year=2026&typeCode=ANNUAL&page=0&size=50 */
    /* 정렬: 입사일 오름차순 (Service 레이어 고정, Pageable.sort 무시) */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping("/departments/{deptId}/members")
    public ResponseEntity<Page<DepartmentMemberVacationResponseDto>> getDepartmentMembers(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long deptId,
            @RequestParam Integer year,
            @RequestParam(required = false, defaultValue = VacationType.CODE_ANNUAL) String typeCode,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(
                vacationDashboardService.getDepartmentMembers(companyId, deptId, year, typeCode, pageable));
    }
}

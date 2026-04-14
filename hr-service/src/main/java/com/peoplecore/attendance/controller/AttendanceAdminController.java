package com.peoplecore.attendance.controller;

import com.peoplecore.attendance.dto.AttendanceDailyCardRowResDto;
import com.peoplecore.attendance.dto.AttendanceDailyListRowResDto;
import com.peoplecore.attendance.dto.AttendanceDailySummaryResDto;
import com.peoplecore.attendance.dto.PagedResDto;
import com.peoplecore.attendance.entity.AttendanceCardType;
import com.peoplecore.attendance.entity.EmploymentFilter;
import com.peoplecore.attendance.service.AttendanceAdminService;

import com.peoplecore.auth.RoleRequired;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 근태 현황 관리자 API (Phase 1 - 일자별 탭).
 *
 * 권한:
 *  - HR_SUPER_ADMIN / HR_ADMIN 만 접근 가능 (@RoleRequired).
 *
 * 공통 파라미터:
 *  - X-User-Company 헤더: 회사 UUID
 *  - date: 조회 기준일
 *  - employmentFilter: 재직상태 필터 (ALL / ACTIVE / ON_LEAVE). 생략 시 ALL.
 */
@RestController
@RequestMapping("/attendance/admin/daily")
public class AttendanceAdminController {

    private final AttendanceAdminService adminService;

    @Autowired
    public AttendanceAdminController(AttendanceAdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * 일자별 상단 10개 카드 카운트.
     */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping("/summary")
    public ResponseEntity<AttendanceDailySummaryResDto> getSummary(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "ALL") EmploymentFilter employmentFilter
    ) {
        return ResponseEntity.ok(adminService.getSummary(companyId, date, employmentFilter));
    }

    /**
     * 일자별 사원 테이블 (페이지네이션).
     *
     * GET /attendance/admin/daily/list
     *   ?date=yyyy-MM-dd
     *   &employmentFilter=ALL|ACTIVE|ON_LEAVE    (default ALL)
     *   &deptId=1                                 (optional)
     *   &workGroupId=2                            (optional)
     *   &statuses=LATE,UNAPPROVED_OT              (optional, 복수값)
     *   &keyword=홍길동                            (optional, 사번/이름/부서명 부분일치)
     *   &page=0&size=10                           (default 0 / 10)
     */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})                          // HR 관리자만 접근 가능
    @GetMapping("/list")                                                   // GET /attendance/admin/daily/list
    public ResponseEntity<PagedResDto<AttendanceDailyListRowResDto>> getList(
            @RequestHeader("X-User-Company") UUID companyId,               // 회사 UUID (Gateway 주입)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)   // yyyy-MM-dd → LocalDate
                    LocalDate date,
            @RequestParam(required = false, defaultValue = "ALL")          // 기본 ALL
                    EmploymentFilter employmentFilter,
            @RequestParam(required = false) Long deptId,                   // 부서 필터 (nullable)
            @RequestParam(required = false) Long workGroupId,              // 근무그룹 필터 (nullable)
            @RequestParam(required = false) List<AttendanceCardType> statuses, // 카드 필터 (nullable/empty → 미적용)
            @RequestParam(required = false) String keyword,                // 사번/이름/부서명 LIKE (nullable/blank → 미적용)
            @RequestParam(required = false, defaultValue = "0") int page,  // 0-based 페이지
            @RequestParam(required = false, defaultValue = "10") int size  // 페이지 크기
    ) {
        // 서비스 위임 — 응답 래핑만 담당
        return ResponseEntity.ok(adminService.getList(
                companyId, date, employmentFilter, deptId, workGroupId, statuses, keyword, page, size));
    }

    /**
     * 카드 드릴다운 — 특정 카드 타입에 해당하는 사원 목록.
     *
     * GET /attendance/admin/daily/card
     *   ?date=yyyy-MM-dd
     *   &cardType=LATE                            (필수)
     *   &employmentFilter=ALL                     (optional)
     *   &page=0&size=10
     */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})                          // HR 관리자만
    @GetMapping("/card")                                                   // GET /attendance/admin/daily/card
    public ResponseEntity<PagedResDto<AttendanceDailyCardRowResDto>> getCard(
            @RequestHeader("X-User-Company") UUID companyId,               // 회사 UUID
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)   // 기준일
                    LocalDate date,
            @RequestParam AttendanceCardType cardType,                     // 드릴다운 대상 카드 (필수)
            @RequestParam(required = false, defaultValue = "ALL")          // 기본 ALL
                    EmploymentFilter employmentFilter,
            @RequestParam(required = false, defaultValue = "0") int page,  // 페이지 번호
            @RequestParam(required = false, defaultValue = "10") int size  // 페이지 크기
    ) {
        // 서비스 위임
        return ResponseEntity.ok(adminService.getCard(
                companyId, date, cardType, employmentFilter, page, size));
    }
}

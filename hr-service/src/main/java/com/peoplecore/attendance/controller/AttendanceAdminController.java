package com.peoplecore.attendance.controller;

import com.peoplecore.attendance.dto.AttendanceDailyCardRowResDto;
import com.peoplecore.attendance.dto.AttendanceDailyListRowResDto;
import com.peoplecore.attendance.dto.AttendanceDailySummaryResDto;
import com.peoplecore.attendance.dto.AttendanceDeptSummaryResDto;
import com.peoplecore.attendance.dto.AttendanceEmployeeHistoryResDto;
import com.peoplecore.attendance.dto.AttendanceOvertimeRowResDto;
import com.peoplecore.attendance.dto.AttendancePeriodListRowResDto;
import com.peoplecore.attendance.dto.AttendanceWeeklyDailyStatsResDto;
import com.peoplecore.attendance.dto.AttendanceWeeklyHeadlineResDto;
import com.peoplecore.attendance.dto.PagedResDto;
import com.peoplecore.attendance.entity.AttendanceCardType;
import com.peoplecore.attendance.entity.EmploymentFilter;
import com.peoplecore.attendance.service.AttendanceAdminService;
import com.peoplecore.attendance.service.AttendanceAggregateService;

import com.peoplecore.auth.RoleRequired;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final AttendanceAggregateService aggregateService; // 집계탭(주간) 서비스

    @Autowired
    public AttendanceAdminController(AttendanceAdminService adminService,
                                     AttendanceAggregateService aggregateService) {
        this.adminService = adminService;
        this.aggregateService = aggregateService;
    }

    /**
     * 집계탭 상단 4개 카드 (출근율 / 지각율 / 결근 사원수 / 주간 최대근무 초과 사원수).
     *
     * GET /attendance/admin/daily/aggregate/headline
     *   ?weekStart=yyyy-MM-dd                    (해당 주의 임의 요일 가능, 서비스에서 월요일로 정규화)
     *   &employmentFilter=ALL|ACTIVE|ON_LEAVE    (default ALL)
     */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping("/aggregate/headline")
    public ResponseEntity<AttendanceWeeklyHeadlineResDto> getAggregateHeadline(
            @RequestHeader("X-User-Company") UUID companyId,                // 회사 UUID
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)    // 주 시작일 (월요일로 정규화됨)
                    LocalDate weekStart,
            @RequestParam(required = false, defaultValue = "ALL")           // 재직상태 필터
                    EmploymentFilter employmentFilter
    ) {
        return ResponseEntity.ok(
                aggregateService.getWeeklyHeadline(companyId, weekStart, employmentFilter)
        );
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

    /**
     * 기간별 사원 테이블 (페이지네이션).
     *
     * GET /attendance/admin/period/list
     *   ?start=yyyy-MM-dd
     *   &end=yyyy-MM-dd
     *   &employmentFilter=ALL|ACTIVE|ON_LEAVE    (default ALL)
     *   &deptId=1                                 (optional)
     *   &workGroupId=2                            (optional)
     *   &statuses=LATE,UNAPPROVED_OT              (optional, 복수값)
     *   &keyword=홍길동                            (optional)
     *   &page=0&size=10                           (default 0 / 10)
     *
     * 응답 행 구조는 일자별 list 와 동일하되 workDate 필드가 추가됨.
     */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping("/period/list")
    public ResponseEntity<PagedResDto<AttendancePeriodListRowResDto>> getPeriodList(
            @RequestHeader("X-User-Company") UUID companyId,                // 회사 UUID
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)    // 조회 시작일 (포함)
                    LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)    // 조회 종료일 (포함)
                    LocalDate end,
            @RequestParam(required = false, defaultValue = "ALL")           // 기본 ALL
                    EmploymentFilter employmentFilter,
            @RequestParam(required = false) Long deptId,                    // 부서 필터 (nullable)
            @RequestParam(required = false) Long workGroupId,               // 근무그룹 필터 (nullable)
            @RequestParam(required = false) List<AttendanceCardType> statuses, // 카드 필터 (nullable)
            @RequestParam(required = false) String keyword,                 // 사번/이름/부서명 LIKE
            @RequestParam(required = false, defaultValue = "0") int page,   // 0-based 페이지
            @RequestParam(required = false, defaultValue = "10") int size   // 페이지 크기
    ) {
        // 서비스 위임 — 응답 래핑만 담당
        return ResponseEntity.ok(adminService.getPeriodList(
                companyId, start, end, employmentFilter, deptId, workGroupId, statuses, keyword, page, size));
    }

    /**
     * 주간현황(일자별) — 해당 주 월~일 각 일자별 전사 집계.
     *
     * GET /attendance/admin/weekly-stats
     *   ?weekStart=yyyy-MM-dd                    (해당 주 월요일로 자동 정규화)
     *   &employmentFilter=ALL|ACTIVE|ON_LEAVE    (default ALL)
     */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping("/weekly-stats")
    public ResponseEntity<List<AttendanceWeeklyDailyStatsResDto>> getWeeklyStats(
            @RequestHeader("X-User-Company") UUID companyId,                // 회사 UUID
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)    // 주 시작일 (임의 요일 가능)
                    LocalDate weekStart,
            @RequestParam(required = false, defaultValue = "ALL")           // 기본 ALL
                    EmploymentFilter employmentFilter
    ) {
        return ResponseEntity.ok(adminService.getWeeklyStats(companyId, weekStart, employmentFilter));
    }

    /**
     * 부서별현황 — 주간 단위 부서별 집계.
     *
     * GET /attendance/admin/dept-summary
     *   ?weekStart=yyyy-MM-dd
     *   &employmentFilter=ALL|ACTIVE|ON_LEAVE
     */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping("/dept-summary")
    public ResponseEntity<List<AttendanceDeptSummaryResDto>> getDeptSummary(
            @RequestHeader("X-User-Company") UUID companyId,                // 회사 UUID
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)    // 주 시작일
                    LocalDate weekStart,
            @RequestParam(required = false, defaultValue = "ALL")
                    EmploymentFilter employmentFilter
    ) {
        return ResponseEntity.ok(adminService.getDeptSummary(companyId, weekStart, employmentFilter));
    }

    /**
     * 초과근무 탭 — 주간 사원별 근무시간/초과/상태.
     *
     * GET /attendance/admin/overtime
     *   ?weekStart=yyyy-MM-dd
     *   &employmentFilter=ALL|ACTIVE|ON_LEAVE
     *   &keyword=홍길동                            (optional, 사번/이름/부서명 LIKE)
     *   &page=0&size=10
     */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping("/overtime")
    public ResponseEntity<PagedResDto<AttendanceOvertimeRowResDto>> getOvertimeList(
            @RequestHeader("X-User-Company") UUID companyId,                // 회사 UUID
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)    // 주 시작일
                    LocalDate weekStart,
            @RequestParam(required = false, defaultValue = "ALL")
                    EmploymentFilter employmentFilter,
            @RequestParam(required = false) String keyword,                 // 사번/이름/부서명 LIKE
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(adminService.getOvertimeList(
                companyId, weekStart, employmentFilter, keyword, page, size));
    }

    /**
     * 사원 일별 근무 현황 (상세 모달).
     *
     * GET /attendance/admin/daily/employee/{empId}/history
     *   ?date=yyyy-MM-dd                         (필수, 주간 근무시간 계산 기준)
     *   &cardType=MISSING_COMMUTE                 (optional, 상단 "카테고리" 에코용)
     *   &page=0&size=10                           (default 0 / 10)
     *
     * - 응답 루트 = { header, history(PagedResDto) }
     * - history: 입사일 ~ date 범위 commute_record, workDate DESC
     * - 결근(평일인데 기록 없음)은 행에 포함되지 않음
     */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping("/employee/{empId}/history")
    public ResponseEntity<AttendanceEmployeeHistoryResDto> getEmployeeHistory(
            @RequestHeader("X-User-Company") UUID companyId,                // 회사 UUID
            @PathVariable Long empId,                                       // 대상 사원 PK
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)    // 조회 기준일
                    LocalDate date,
            @RequestParam(required = false) AttendanceCardType cardType,    // 드릴다운 카드 (에코)
            @RequestParam(required = false, defaultValue = "0") int page,   // 0-based
            @RequestParam(required = false, defaultValue = "10") int size   // 페이지 크기
    ) {
        return ResponseEntity.ok(adminService.getEmployeeHistory(
                companyId, empId, date, cardType, page, size));
    }
}

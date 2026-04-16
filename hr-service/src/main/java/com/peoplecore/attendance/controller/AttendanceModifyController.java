package com.peoplecore.attendance.controller;

import com.peoplecore.attendance.dto.*;
import com.peoplecore.attendance.entity.ModifyStatus;
import com.peoplecore.attendance.service.AttendanceModifyService;
import com.peoplecore.auth.RoleRequired;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/*
 * 근태 정정 컨트롤러.
 */
@RestController
@RequestMapping("/attendance/modify")
public class AttendanceModifyController {

    private final AttendanceModifyService attendanceModifyService;

    @Autowired
    public AttendanceModifyController(AttendanceModifyService attendanceModifyService) {
        this.attendanceModifyService = attendanceModifyService;
    }

    /*
     * 모달 프리필 — 날짜 선택 시 해당 CommuteRecord 현재값 + 사원 스냅샷 + formId 반환.
     */
    @GetMapping("/prefill")
    public ResponseEntity<AttendanceModifyPrefillResDto> prefill(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate
    ) {
        return ResponseEntity.ok(attendanceModifyService.prefill(companyId, empId, workDate));
    }

    /*
     * 단건 상세 — HR 관리자/본인 공용. 회사 소속 검증은 Service 에서.
     */
    @GetMapping("/{attenModiId}")
    public ResponseEntity<AttendanceModifyResDto> getDetail(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long attenModiId
    ) {
        return ResponseEntity.ok(attendanceModifyService.getDetail(companyId, attenModiId));
    }

    /*
     * HR 관리자 목록 — 회사 전체 정정 신청 (status 필터 옵션).
     */
    @RoleRequired({"HR_ADMIN", "HR_SUPER_ADMIN"})
    @GetMapping("/admin")
    public ResponseEntity<Page<AttendanceModifyListResDto>> listForAdmin(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam(required = false) ModifyStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(attendanceModifyService.getListForAdmin(companyId, status, pageable));
    }

    /*
     * 본인 신청 이력 — X-User-Id 로 강제 필터. 남의 이력 조회 불가.
     */
    @GetMapping("/my")
    public ResponseEntity<Page<AttendanceModifyListResDto>> myHistory(
            @RequestHeader("X-User-Id") Long empId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(attendanceModifyService.getMyHistory(empId, pageable));
    }

    /*
     * 주간 근태 그리드 — 날짜 선택 UI 렌더용.
     * weekStart 는 해당 주 월요일로 정규화 (어느 요일이 들어와도 OK).
     */
    @GetMapping("/week")
    public ResponseEntity<AttendanceModifyWeekResDto> getWeek(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart
    ) {
        return ResponseEntity.ok(attendanceModifyService.getWeek(companyId, empId, weekStart));
    }

    /*
     * HR 사원 목록 — 결재선 선택 UI + collab 상신 검증 훅 공용.
     * 권한 제한 없음 (공개) — 결재선 선택 시 모든 사원이 참고 가능해야 함.
     */
    @GetMapping("/hr-members")
    public ResponseEntity<AttendanceModifyHrMemberResDto> getHrMembers(
            @RequestHeader("X-User-Company") UUID companyId
    ) {
        return ResponseEntity.ok(attendanceModifyService.getHrMembers(companyId));
    }
}

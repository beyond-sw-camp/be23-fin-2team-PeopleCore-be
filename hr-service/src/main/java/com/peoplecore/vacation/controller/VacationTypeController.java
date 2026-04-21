package com.peoplecore.vacation.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.vacation.dto.VacationTypeReorderRequest;
import com.peoplecore.vacation.dto.VacationTypeRequest;
import com.peoplecore.vacation.dto.VacationTypeResponse;
import com.peoplecore.vacation.service.VacationTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/* 휴가 유형 Controller - 사원 조회 (활성) + 관리자 CRUD */
@RestController
@RequestMapping("/vacation/types")
public class VacationTypeController {

    private final VacationTypeService vacationTypeService;

    @Autowired
    public VacationTypeController(VacationTypeService vacationTypeService) {
        this.vacationTypeService = vacationTypeService;
    }

    /* 활성 유형 목록 - 사원 휴가 신청 드롭다운. 로그인 사원 누구나 조회 */
    @GetMapping
    public ResponseEntity<List<VacationTypeResponse>> listActive(
            @RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(vacationTypeService.listActive(companyId));
    }

    /* 전체 유형 목록 - 관리자 화면 (비활성 포함) */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping("/all")
    public ResponseEntity<List<VacationTypeResponse>> listAll(
            @RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(vacationTypeService.listAll(companyId));
    }

    /* 신규 유형 생성 - typeCode 시스템 예약 차단 */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @PostMapping
    public ResponseEntity<VacationTypeResponse> create(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestBody VacationTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vacationTypeService.create(companyId, request));
    }

    /* 표시 정보 수정 - typeCode 불변 */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @PutMapping("/{typeId}")
    public ResponseEntity<VacationTypeResponse> update(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long typeId,
            @RequestBody VacationTypeRequest request) {
        return ResponseEntity.ok(vacationTypeService.updateDisplay(companyId, typeId, request));
    }

    /* 일괄 재정렬 - 드래그 앤 드롭 결과 반영. 시스템 예약 유형도 순서 변경 가능 */
    /* Body: { "items": [{ "typeId": 5, "sortOrder": 1 }, ...] } */
    /* 응답: 재정렬 적용된 전체 유형 목록 (sortOrder 오름차순) */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @PutMapping("/reorder")
    public ResponseEntity<List<VacationTypeResponse>> reorder(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestBody VacationTypeReorderRequest request) {
        return ResponseEntity.ok(vacationTypeService.reorder(companyId, request));
    }

    /* 비활성화 - 기존 잔여는 사용 가능, 신규 신청만 막음 */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @DeleteMapping("/{typeId}")
    public ResponseEntity<Void> deactivate(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long typeId) {
        vacationTypeService.deactivate(companyId, typeId);
        return ResponseEntity.noContent().build();
    }

    /* 재활성화 */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @PostMapping("/{typeId}/activate")
    public ResponseEntity<Void> activate(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long typeId) {
        vacationTypeService.activate(companyId, typeId);
        return ResponseEntity.noContent().build();
    }

    /* 물리 삭제 - 참조 0 건일 때만. 시스템 예약/사용 중이면 4xx */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @DeleteMapping("/{typeId}/hard")
    public ResponseEntity<Void> hardDelete(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long typeId) {
        vacationTypeService.hardDelete(companyId, typeId);
        return ResponseEntity.noContent().build();
    }
}

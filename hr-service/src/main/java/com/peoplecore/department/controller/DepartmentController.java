package com.peoplecore.department.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.department.dto.DepartmentCreateRequest;
import com.peoplecore.department.dto.DepartmentDetailResponse;
import com.peoplecore.department.dto.DepartmentResponse;
import com.peoplecore.department.dto.DepartmentUpdateRequest;
import com.peoplecore.department.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    /**
     * 조직도 트리 조회 (하위 부서 포함 계층 구조)
     */
    @GetMapping("/tree")
    public ResponseEntity<List<DepartmentResponse>> getOrgTree(
            @RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(departmentService.getOrgTree(companyId));
    }

    /**
     * 전체 부서 플랫 리스트 조회
     */
    @GetMapping
    public ResponseEntity<List<DepartmentResponse>> getAllDepartments(
            @RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(departmentService.getAllDepartments(companyId));
    }

    /**
     * 부서 단건 조회
     */
    @GetMapping("/{deptId}")
    public ResponseEntity<DepartmentResponse> getDepartment(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long deptId) {
        return ResponseEntity.ok(departmentService.getDepartment(companyId, deptId));
    }

    /**
     * 부서 상세 조회 (직책 보유자, 재직 인원 수, 하위 부서 수)
     */
    @GetMapping("/{deptId}/detail")
    public ResponseEntity<DepartmentDetailResponse> getDepartmentDetail(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long deptId) {
        return ResponseEntity.ok(departmentService.getDepartmentDetail(companyId, deptId));
    }

    /**
     * 부서 등록
     */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @PostMapping
    public ResponseEntity<DepartmentResponse> createDepartment(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestBody DepartmentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(departmentService.createDepartment(companyId, request));
    }

    /**
     * 부서 수정
     */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @PutMapping("/{deptId}")
    public ResponseEntity<DepartmentResponse> updateDepartment(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long deptId,
            @RequestBody DepartmentUpdateRequest request) {
        return ResponseEntity.ok(departmentService.updateDepartment(companyId, deptId, request));
    }

    /**
     * 부서 삭제 (비활성화)
     */
    @RoleRequired({"HR_SUPER_ADMIN"})
    @DeleteMapping("/{deptId}")
    public ResponseEntity<Void> deleteDepartment(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long deptId) {
        departmentService.deleteDepartment(companyId, deptId);
        return ResponseEntity.noContent().build();
    }
}

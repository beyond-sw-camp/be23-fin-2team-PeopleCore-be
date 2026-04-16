package com.peoplecore.permission.controller;

import com.peoplecore.employee.domain.EmpRole;
import com.peoplecore.permission.dto.AdminUserResDto;
import com.peoplecore.permission.dto.PermissionHistoryResDto;
import com.peoplecore.permission.service.PermissionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    // ADMIN + SUPER_ADMIN 목록 조회 (페이징, 검색, 필터, 정렬)
    @GetMapping("/admins")
    public ResponseEntity<Page<AdminUserResDto>> getAdminList(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) EmpRole empRole,
            @RequestParam(required = false) String sortField,
            Pageable pageable) {
        if (!"HR_SUPER_ADMIN".equals(role)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(
                permissionService.getAdminList(companyId, keyword, deptId, empRole, sortField, pageable)
        );
    }

    // SUPER_ADMIN 권한 부여
    @PutMapping("/admins/{empId}/grant")
    public ResponseEntity<Void> grantSuperAdmin(
            @PathVariable Long empId,
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader("X-User-Emp") Long grantorEmpId) {
        if (!"HR_SUPER_ADMIN".equals(role)) {
            return ResponseEntity.status(403).build();
        }
        permissionService.grantSuperAdmin(empId, companyId, grantorEmpId);
        return ResponseEntity.ok().build();
    }

    // SUPER_ADMIN 권한 회수
    @PutMapping("/admins/{empId}/revoke")
    public ResponseEntity<Void> revokeSuperAdmin(
            @PathVariable Long empId,
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader("X-User-Emp") Long grantorEmpId) {
        if (!"HR_SUPER_ADMIN".equals(role)) {
            return ResponseEntity.status(403).build();
        }
        permissionService.revokeSuperAdmin(empId, companyId, grantorEmpId);
        return ResponseEntity.ok().build();
    }

    // 권한 변경 이력 조회 (최신순)
    @GetMapping("/history")
    public ResponseEntity<List<PermissionHistoryResDto>> getHistory(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Role") String role) {
        if (!"HR_SUPER_ADMIN".equals(role)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(permissionService.getHistory(companyId));
    }
}

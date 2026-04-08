package com.peoplecore.approval.controller;

import com.peoplecore.approval.dto.AdminDelegationCreateRequest;
import com.peoplecore.approval.dto.ApprovalDelegationResponse;
import com.peoplecore.approval.service.ApprovalDelegationService;
import com.peoplecore.auth.RoleRequired;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/approval/admin/delegations")
public class AdminDelegationController {

    private final ApprovalDelegationService delegationService;

    @Autowired
    public AdminDelegationController(ApprovalDelegationService delegationService) {
        this.delegationService = delegationService;
    }

    /** 관리자 - 위임 전체 목록 조회 */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @GetMapping
    public ResponseEntity<List<ApprovalDelegationResponse>> getAllDelegations(
            @RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(delegationService.getAllDelegations(companyId));
    }

    /** 관리자 - 위임 대리 등록 */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @PostMapping
    public ResponseEntity<Long> create(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestBody AdminDelegationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(delegationService.createByAdmin(companyId, request));
    }

    /** 관리자 - 위임 삭제 */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long id) {
        delegationService.deleteByAdmin(companyId, id);
        return ResponseEntity.ok().build();
    }

    /** 관리자 - 위임 활성/비활성 토글 */
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<Void> toggle(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long id) {
        delegationService.toggleByAdmin(companyId, id);
        return ResponseEntity.ok().build();
    }
}

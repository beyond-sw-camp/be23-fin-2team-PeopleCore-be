package com.peoplecore.approval.controller;

import com.peoplecore.approval.dto.ApprovalDelegationCreateRequest;
import com.peoplecore.approval.dto.ApprovalDelegationResponse;
import com.peoplecore.approval.service.ApprovalDelegationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/approval/delegations")
public class ApprovalDelegationController {

    private final ApprovalDelegationService delegationService;

    @Autowired
    public ApprovalDelegationController(ApprovalDelegationService delegationService) {
        this.delegationService = delegationService;
    }

    /** 내 위임 목록 조회 */
    @GetMapping
    public ResponseEntity<List<ApprovalDelegationResponse>> getMyDelegations(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(delegationService.getMyDelegations(companyId, empId));
    }

    /** 위임 등록 */
    @PostMapping
    public ResponseEntity<Long> create(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestHeader("X-User-Name") String empName,
            @RequestBody ApprovalDelegationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(delegationService.create(companyId, empId, empName, request));
    }

    /** 위임 삭제 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long id) {
        delegationService.delete(companyId, empId, id);
        return ResponseEntity.ok().build();
    }

    /** isActive 토글 */
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<Void> toggle(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long id) {
        delegationService.toggle(companyId, empId, id);
        return ResponseEntity.ok().build();
    }
}
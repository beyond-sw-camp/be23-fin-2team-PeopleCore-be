package com.peoplecore.approval.controller;

import com.peoplecore.approval.dto.AutoClassifyRuleCreateRequest;
import com.peoplecore.approval.dto.AutoClassifyRuleReorderRequest;
import com.peoplecore.approval.dto.AutoClassifyRuleResponse;
import com.peoplecore.approval.dto.AutoClassifyRuleUpdateRequest;
import com.peoplecore.approval.service.AutoClassifyRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/approval/auto-classify-rules")
public class AutoClassifyRuleController {

    private final AutoClassifyRuleService ruleService;

    @Autowired
    public AutoClassifyRuleController(AutoClassifyRuleService ruleService) {
        this.ruleService = ruleService;
    }

    /** 11. 규칙 목록 조회 */
    // TODO: @RoleRequired({"HR_SUPER_ADMIN"})
    @GetMapping
    public ResponseEntity<List<AutoClassifyRuleResponse>> getList(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Department") Long deptId) {
        return ResponseEntity.ok(ruleService.getList(companyId, deptId));
    }

    /** 12. 규칙 생성 */
    // TODO: @RoleRequired({"HR_SUPER_ADMIN"})
    @PostMapping
    public ResponseEntity<AutoClassifyRuleResponse> create(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Department") Long deptId,
            @RequestBody AutoClassifyRuleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ruleService.create(companyId, deptId, request));
    }

    /** 13. 규칙 수정 */
    // TODO: @RoleRequired({"HR_SUPER_ADMIN"})
    @PutMapping("/{id}")
    public ResponseEntity<AutoClassifyRuleResponse> update(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long id,
            @RequestBody AutoClassifyRuleUpdateRequest request) {
        return ResponseEntity.ok(ruleService.update(companyId, id, request));
    }

    /** 14. 규칙 삭제 */
    // TODO: @RoleRequired({"HR_SUPER_ADMIN"})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long id) {
        ruleService.delete(companyId, id);
        return ResponseEntity.noContent().build();
    }

    /** 15. 활성/비활성 토글 */
    // TODO: @RoleRequired({"HR_SUPER_ADMIN"})
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<Void> toggle(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long id) {
        ruleService.toggle(companyId, id);
        return ResponseEntity.ok().build();
    }

    /** 16. 규칙 순서 변경 */
    // TODO: @RoleRequired({"HR_SUPER_ADMIN"})
    @PutMapping("/reorder")
    public ResponseEntity<Void> reorder(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestBody AutoClassifyRuleReorderRequest request) {
        ruleService.reorder(companyId, request);
        return ResponseEntity.ok().build();
    }
}

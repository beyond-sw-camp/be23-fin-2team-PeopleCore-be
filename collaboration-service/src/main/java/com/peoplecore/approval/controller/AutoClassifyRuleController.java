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

    // 목록 조회 — deptId → empId
    @GetMapping
    public ResponseEntity<List<AutoClassifyRuleResponse>> getList(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(ruleService.getList(companyId, empId));
    }

    // 생성 — deptId → empId
    @PostMapping
    public ResponseEntity<AutoClassifyRuleResponse> create(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody AutoClassifyRuleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ruleService.create(companyId, empId, request));
    }

    // 수정/삭제/토글/순서변경 — companyId + empId 기준으로 변경
    @PutMapping("/{id}")
    public ResponseEntity<AutoClassifyRuleResponse> update(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long id,
            @RequestBody AutoClassifyRuleUpdateRequest request) {
        return ResponseEntity.ok(ruleService.update(companyId, empId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long id) {
        ruleService.delete(companyId, empId, id);
        return ResponseEntity.noContent().build();
    }


    /** 활성/비활성 토글 */
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<Void> toggle(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long id) {
        ruleService.toggle(companyId, empId, id);
        return ResponseEntity.ok().build();
    }

    /** 규칙 순서 변경 */
    @PutMapping("/reorder")
    public ResponseEntity<Void> reorder(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody AutoClassifyRuleReorderRequest request) {
        ruleService.reorder(companyId, empId, request);
        return ResponseEntity.ok().build();
    }
}

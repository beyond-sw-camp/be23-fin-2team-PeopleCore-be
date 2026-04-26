package com.peoplecore.evaluation.controller;

import com.peoplecore.evaluation.domain.EvaluatorRoleMode;
import com.peoplecore.evaluation.dto.EvaluatorRoleConfigResponse;
import com.peoplecore.evaluation.dto.EvaluatorRolePreviewResponse;
import com.peoplecore.evaluation.dto.EvaluatorRoleUpdateRequest;
import com.peoplecore.evaluation.dto.MyEvaluatorRoleResponse;
import com.peoplecore.evaluation.service.EvaluatorRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// 평가자 역할 설정 API. /config, /preview 는 HR_ADMIN 이상 (HR_ADMIN | HR_SUPER_ADMIN). /me 는 전 사원 호출 가능.
@RestController
@RequestMapping("/evaluator-role")
@RequiredArgsConstructor
public class EvaluatorRoleController {

    private final EvaluatorRoleService service;

    private static boolean isHrAdminOrAbove(String role) {
        return "HR_ADMIN".equals(role) || "HR_SUPER_ADMIN".equals(role);
    }

    // 회사 현재 평가자 설정 조회.
    @GetMapping("/config")
    public ResponseEntity<EvaluatorRoleConfigResponse> getConfig(
        @RequestHeader("X-User-Company") UUID companyId,
        @RequestHeader("X-User-Role") String role
    ) {
        if (!isHrAdminOrAbove(role)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(service.getConfig(companyId));
    }

    // 평가자 설정 저장
    @PutMapping("/config")
    public ResponseEntity<EvaluatorRoleConfigResponse> updateConfig(
        @RequestHeader("X-User-Company") UUID companyId,
        @RequestHeader("X-User-Role") String role,
        @Valid @RequestBody EvaluatorRoleUpdateRequest request
    ) {
        if (!isHrAdminOrAbove(role)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(service.updateConfig(companyId, request));
    }

    // mode+targetId 에 대한 부서별 매칭 결과 미리보기
    @GetMapping("/preview")
    public ResponseEntity<EvaluatorRolePreviewResponse> preview(
        @RequestHeader("X-User-Company") UUID companyId,
        @RequestHeader("X-User-Role") String role,
        @RequestParam EvaluatorRoleMode mode,
        @RequestParam Long targetId
    ) {
        if (!isHrAdminOrAbove(role)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(service.preview(companyId, mode, targetId));
    }

    // 로그인 사용자가 평가자인지 여부. 전 사원 호출 — 사이드바 메뉴 분기용.
    @GetMapping("/me")
    public ResponseEntity<MyEvaluatorRoleResponse> me(
        @RequestHeader("X-User-Company") UUID companyId,
        @RequestHeader("X-User-Id") Long empId
    ) {
        return ResponseEntity.ok(service.me(companyId, empId));
    }
}

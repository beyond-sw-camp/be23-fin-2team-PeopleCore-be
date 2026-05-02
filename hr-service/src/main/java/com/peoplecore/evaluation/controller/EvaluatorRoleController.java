package com.peoplecore.evaluation.controller;

import com.peoplecore.evaluation.dto.MyEvaluatorRoleResponse;
import com.peoplecore.evaluation.service.EvaluatorRoleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// 평가자 역할 조회 API. /me 만 — 사이드바 메뉴 분기용. 전 사원 호출 가능.
@RestController
@RequestMapping("/evaluator-role")
public class EvaluatorRoleController {

    private final EvaluatorRoleService service;

    public EvaluatorRoleController(EvaluatorRoleService service) {
        this.service = service;
    }

    // 로그인 사용자가 평가자인지 여부 (사이드바 메뉴 분기용)
    @GetMapping("/me")
    public ResponseEntity<MyEvaluatorRoleResponse> me(@RequestHeader("X-User-Company") UUID companyId,
                                                      @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(service.me(companyId, empId));
    }
}

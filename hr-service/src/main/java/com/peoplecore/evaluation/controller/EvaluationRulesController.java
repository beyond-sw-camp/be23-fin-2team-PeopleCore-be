package com.peoplecore.evaluation.controller;

import com.peoplecore.evaluation.dto.EvaluationRulesDto;
import com.peoplecore.evaluation.dto.EvaluationRulesSaveRequestDto;
import com.peoplecore.evaluation.service.EvaluationRulesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// 평가규칙 - 회사당 1 row, 회사기준으로 자유 편집
@RestController
@RequestMapping("/eval/rules")
@RequiredArgsConstructor
public class EvaluationRulesController {

    private final EvaluationRulesService rulesService;

    // 회사 규칙 조회 — 회사 생성 시 기본값으로 초기화되므로 null 반환 케이스 없음
    @GetMapping
    public ResponseEntity<EvaluationRulesDto> get(@RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(rulesService.getByCompanyId(companyId));
    }

    // 회사 규칙 저장/수정 — 시즌 상태와 무관하게 항상 편집 가능
    @PutMapping
    public ResponseEntity<EvaluationRulesDto> save(@RequestHeader("X-User-Company") UUID companyId,
                                                   @RequestBody EvaluationRulesSaveRequestDto request) {
        return ResponseEntity.ok(rulesService.save(companyId, request));
    }
}

package com.peoplecore.evaluation.controller;

import com.peoplecore.evaluation.dto.EvaluationRulesDto;
import com.peoplecore.evaluation.dto.EvaluationRulesSaveRequestDto;
import com.peoplecore.evaluation.service.EvaluationRulesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 평가규칙 - 항목/등급/가감점 커스텀 규칙
@RestController
@RequestMapping("/eval/rules")
@RequiredArgsConstructor
public class EvaluationRulesController {

    private final EvaluationRulesService rulesService;

    // 시즌 규칙 조회 (없으면 200 + null)
    @GetMapping("/{seasonId}")
    public ResponseEntity<EvaluationRulesDto> get(@PathVariable Long seasonId) {
        return ResponseEntity.ok(rulesService.getBySeasonId(seasonId));
    }

    // 시즌 규칙 저장/수정 (DRAFT)
    @PutMapping("/{seasonId}")
    public ResponseEntity<EvaluationRulesDto> save(@PathVariable Long seasonId,
                                                   @RequestBody EvaluationRulesSaveRequestDto request) {
        return ResponseEntity.ok(rulesService.save(seasonId, request));
    }

    // 시즌 OPEN 시 스냅샷 동결 (시즌 상태 전이 로직에서 호출) //TODO스케줄러로 변경 -오픈 마감 생성시
    @PostMapping("/{seasonId}/freeze")
    public ResponseEntity<Void> freeze(@PathVariable Long seasonId) {
        rulesService.freezeSnapshot(seasonId);
        return ResponseEntity.noContent().build();
    }
}

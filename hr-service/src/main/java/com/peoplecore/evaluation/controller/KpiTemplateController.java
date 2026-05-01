package com.peoplecore.evaluation.controller;

import com.peoplecore.evaluation.dto.KpiTemplateRequest;
import com.peoplecore.evaluation.dto.KpiTemplateResponse;
import com.peoplecore.evaluation.service.KpiTemplateService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// KPI지표 템플릿 - 부서/카테고리별 지표 관리
@RestController
@RequestMapping("/eval/kpi-templates")
public class KpiTemplateController {

    private final KpiTemplateService kpiTemplateService;

    public KpiTemplateController(KpiTemplateService kpiTemplateService) {
        this.kpiTemplateService = kpiTemplateService;
    }

    // 1. 목록 조회 (필터 + 페이징 + 검색)
    //    gradeId 선택 시: 해당 직급 OR 전 직급 공통(null) KPI 모두 노출
    @GetMapping
    public ResponseEntity<Page<KpiTemplateResponse>> getTemplates(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Long gradeId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            Pageable pageable) {
        return ResponseEntity.ok(
                kpiTemplateService.getTemplates(companyId, deptId, gradeId, category, keyword, pageable));
    }

    // 2. 단건 조회 - 수정 모달 prefill 용
    @GetMapping("/{id}")
    public ResponseEntity<KpiTemplateResponse> getTemplate(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long id) {
        return ResponseEntity.ok(kpiTemplateService.getTemplate(companyId, id));
    }

    // 3. 신규 등록
    //    - 등록 시 baseline 은 항상 NULL (집계 대기)
    @PostMapping
    public ResponseEntity<KpiTemplateResponse> createTemplate(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestBody @Valid KpiTemplateRequest request) {
        return ResponseEntity.ok(kpiTemplateService.createTemplate(companyId, request));
    }

    // 4. 수정 - 부서/카테고리/이름/설명/단위 변경
    @PutMapping("/{id}")
    public ResponseEntity<KpiTemplateResponse> updateTemplate(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long id,
            @RequestBody @Valid KpiTemplateRequest request) {
        return ResponseEntity.ok(kpiTemplateService.updateTemplate(companyId, id, request));
    }

    // 5. 삭제
    //    - Goal 이 이 템플릿을 참조 중이면 service 에서 차단 (FK 보호)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long id) {
        kpiTemplateService.deleteTemplate(companyId, id);
        return ResponseEntity.noContent().build();
    }
}

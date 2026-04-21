package com.peoplecore.evaluation.controller;

import com.peoplecore.evaluation.dto.KpiOptionBundleRequest;
import com.peoplecore.evaluation.dto.KpiOptionBundleResponse;
import com.peoplecore.evaluation.service.KpiOptionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// KPI 옵션 - 카테고리 / 부서 depth 설정 / 단위 라벨
@RestController
@RequestMapping("/eval/kpi-option")
public class KpiOptionController {

    private final KpiOptionService service;

    public KpiOptionController(KpiOptionService service) {
        this.service = service;
    }

    // 1. KPI 옵션 조회 (카테고리 / 부서 depth / 단위 라벨 묶음)
    @GetMapping
    public ResponseEntity<KpiOptionBundleResponse> getOptions(
            @RequestHeader("X-User-Company") String companyId) {
        return ResponseEntity.ok(service.getOptions(UUID.fromString(companyId)));
    }

    // 2. KPI 옵션 일괄 저장
    @PutMapping
    public ResponseEntity<KpiOptionBundleResponse> saveOptions(
            @RequestHeader("X-User-Company") String companyId,
            @RequestBody @Valid KpiOptionBundleRequest request) {
        return ResponseEntity.ok(service.saveOptions(UUID.fromString(companyId), request));
    }

    // 3. 기본값 복원
    @PostMapping("/reset")
    public ResponseEntity<KpiOptionBundleResponse> resetOptions(
            @RequestHeader("X-User-Company") String companyId) {
        return ResponseEntity.ok(service.resetOptions(UUID.fromString(companyId)));
    }
}

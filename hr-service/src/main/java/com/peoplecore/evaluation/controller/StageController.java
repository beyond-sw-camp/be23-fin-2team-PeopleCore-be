package com.peoplecore.evaluation.controller;

import com.peoplecore.evaluation.dto.StageDto;
import com.peoplecore.evaluation.dto.StageUpdateRequestDto;
import com.peoplecore.evaluation.service.StageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// 단계 - 시즌별 단계 일정 관리 및 오픈/마감 처리
@RestController
@RequestMapping("/eval/stages")
public class StageController {

    private final StageService stageService;

    public StageController(StageService stageService) {
        this.stageService = stageService;
    }

    // 단계 상태 토글 (임시 개폐) — FINISHED <-> IN_PROGRESS
    @PatchMapping("/{stageId}/toggle-status")
    public ResponseEntity<StageDto> toggleStatus(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long stageId) {
        return ResponseEntity.ok(stageService.toggleStatus(companyId, stageId));
    }

    // 단계 날짜 수정 — 기간 추가(endDate 연장) + SeasonDetail 자유 수정(start/end)
    @PatchMapping("/{stageId}")
    public ResponseEntity<StageDto> updateDates(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long stageId,
            @RequestBody StageUpdateRequestDto request) {
        return ResponseEntity.ok(stageService.updateDates(companyId, stageId, request));
    }
}

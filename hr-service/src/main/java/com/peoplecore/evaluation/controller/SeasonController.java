package com.peoplecore.evaluation.controller;

import com.peoplecore.evaluation.dto.*;
import com.peoplecore.evaluation.service.SeasonService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// 평가시즌 - 시즌 생성/조회/수정/삭제
@RestController
@RequestMapping("/eval/seasons")
public class SeasonController {

    private final SeasonService seasonService;

    public SeasonController(SeasonService seasonService) {
        this.seasonService = seasonService;
    }

    // 1. 시즌 목록 (회사별)
    @GetMapping
    public ResponseEntity<List<SeasonResponseDto>> getSeasons(
            @RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(seasonService.getSeasons(companyId));
    }

    // 2. 활성 시즌 목록 (드롭다운용)
    @GetMapping("/active")
    public ResponseEntity<List<SeasonDropDto>> getActiveSeasons(
            @RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(seasonService.getActiveSeasons(companyId));
    }

    // 3. 시즌 상세 조회 //rule에서 시즌 규칙 조회 getBySeasonId매서드 재사용
    @GetMapping("/{seasonId}")
    public ResponseEntity<SeasonDetailDto> getSeason(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long seasonId) {
        return ResponseEntity.ok(seasonService.getSeasonDetail(companyId, seasonId));
    }

    // 4. 시즌 생성
    @PostMapping
    public ResponseEntity<Long> createSeason(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Emp") Long empId,
            @Valid @RequestBody SeasonCreateRequestDto requestDto) {
        return ResponseEntity.ok(seasonService.createSeason(companyId, empId, requestDto));
    }

    // 5. 시즌 수정 //관리 -> 수정 (ui상 아래 단계수정 =grade쪽 메서드) //closed수정불가
    @PutMapping("/{seasonId}")
    public ResponseEntity<SeasonResponseDto> updateSeason(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long seasonId,
            @Valid @RequestBody SeasonUpdateRequestDto requestDto) {
        return ResponseEntity.ok(seasonService.updateSeason(companyId, seasonId, requestDto));
    }

    // 6. 시즌 삭제 //DRAFT 상태, hardDelete
    @DeleteMapping("/{seasonId}")
    public ResponseEntity<Void> deleteSeason(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long seasonId) {
        seasonService.deleteSeason(companyId, seasonId);
        return ResponseEntity.ok().build();
    }
}

package com.peoplecore.evaluation.service;

import com.peoplecore.evaluation.domain.EvalSeasonStatus;
import com.peoplecore.evaluation.domain.Season;
import com.peoplecore.evaluation.domain.Stage;
import com.peoplecore.evaluation.domain.StageStatus;
import com.peoplecore.evaluation.dto.StageDto;
import com.peoplecore.evaluation.dto.StageUpdateRequestDto;
import com.peoplecore.evaluation.repository.StageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

// 단계 - 시즌별 단계 일정 관리 및 오픈/마감 처리
@Service
@Transactional
public class StageService {

    private final StageRepository stageRepository;

    public StageService(StageRepository stageRepository) {
        this.stageRepository = stageRepository;
    }

    // 단계 상태 토글 (임시 개폐) — FINISHED <-> IN_PROGRESS
    //  - 진행중 시즌(OPEN)에서만 허용
    //  - WAITING 상태는 토글 대상 아님
    public StageDto toggleStatus(UUID companyId, Long stageId) {
        Stage stage = stageRepository.findById(stageId)
                .orElseThrow(() -> new IllegalArgumentException("단계를 찾을 수 없습니다"));

        validateOwnership(stage, companyId);
        validateSeasonOpen(stage.getSeason());

        StageStatus current = stage.getStatus();
        if (current == StageStatus.FINISHED) {
            stage.start();   // 마감 → 진행중 (임시 오픈)
        } else if (current == StageStatus.IN_PROGRESS) {
            stage.finish(); // 진행중 → 마감 (임시 오픈 종료)
        } else {
            throw new IllegalStateException("대기 상태 단계는 개폐할 수 없습니다");
        }
        return StageDto.from(stage);
    }

    // 단계 날짜 수정 — 기간 추가(endDate 연장) + SeasonDetail 자유 수정(start/end 동시)
    //  - CLOSED 시즌은 수정 불가
    //  - 시즌 기간 내로 제한
    //  - endDate >= startDate
    //  - 단계 간 시작일 순서 유지 (이전 단계 ≤ 현재 ≤ 다음 단계), 같은 날짜 허용
    public StageDto updateDates(UUID companyId, Long stageId, StageUpdateRequestDto req) {
        Stage stage = stageRepository.findById(stageId)
                .orElseThrow(() -> new IllegalArgumentException("단계를 찾을 수 없습니다"));

        validateOwnership(stage, companyId);

        Season season = stage.getSeason();
        if (season.getStatus() == EvalSeasonStatus.CLOSED) {
            throw new IllegalStateException("완료된 시즌의 단계는 수정할 수 없습니다");
        }

        if (req.getStartDate() == null && req.getEndDate() == null) {
            throw new IllegalArgumentException("수정할 날짜를 입력하세요");
        }

        LocalDate newStart = req.getStartDate() != null ? req.getStartDate() : stage.getStartDate();
        LocalDate newEnd   = req.getEndDate()   != null ? req.getEndDate()   : stage.getEndDate();

        if (newStart == null || newEnd == null) {
            throw new IllegalArgumentException("시작일/종료일이 비어있습니다");
        }
        if (newEnd.isBefore(newStart)) {
            throw new IllegalArgumentException("종료일이 시작일보다 빠를 수 없습니다");
        }
        if (season.getStartDate() != null && newStart.isBefore(season.getStartDate())) {
            throw new IllegalArgumentException("단계 시작일은 시즌 시작일보다 앞설 수 없습니다");
        }
        if (season.getEndDate() != null && newEnd.isAfter(season.getEndDate())) {
            throw new IllegalArgumentException("단계 종료일은 시즌 종료일을 넘을 수 없습니다");
        }

        // 단계 간 시작일 순서 검증 (같은 날짜는 허용)
        List<Stage> siblings = stageRepository.findBySeason_SeasonId(season.getSeasonId()).stream()
                .sorted(Comparator.comparing(s -> s.getOrderNo() == null ? 0 : s.getOrderNo()))
                .toList();

        int idx = -1;
        for (int i = 0; i < siblings.size(); i++) {
            if (siblings.get(i).getStageId().equals(stage.getStageId())) { idx = i; break; }
        }
        if (idx > 0) {
            Stage prev = siblings.get(idx - 1);
            if (prev.getStartDate() != null && newStart.isBefore(prev.getStartDate())) {
                throw new IllegalArgumentException("단계 시작일은 이전 단계 시작일보다 앞설 수 없습니다");
            }
        }
        if (idx >= 0 && idx < siblings.size() - 1) {
            Stage next = siblings.get(idx + 1);
            if (next.getStartDate() != null && newStart.isAfter(next.getStartDate())) {
                throw new IllegalArgumentException("단계 시작일은 다음 단계 시작일보다 늦을 수 없습니다");
            }
        }

        stage.updateDates(newStart, newEnd);
        return StageDto.from(stage);
    }

    // 공통: 회사 소유권 검증
    private void validateOwnership(Stage stage, UUID companyId) {
        if (stage.getSeason() == null || stage.getSeason().getCompany() == null
                || !stage.getSeason().getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("접근 권한이 없는 단계입니다");
        }
    }

    // 공통: 시즌 OPEN(진행중) 상태만 허용
    private void validateSeasonOpen(Season season) {
        if (season.getStatus() != EvalSeasonStatus.OPEN) {
            throw new IllegalStateException("진행중 시즌에서만 단계를 개폐할 수 있습니다");
        }
    }
}

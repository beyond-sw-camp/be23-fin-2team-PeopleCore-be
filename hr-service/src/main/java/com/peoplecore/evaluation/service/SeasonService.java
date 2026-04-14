package com.peoplecore.evaluation.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.evaluation.domain.EvalSeasonStatus;
import com.peoplecore.evaluation.domain.EvaluationRules;
import com.peoplecore.evaluation.domain.Season;
import com.peoplecore.evaluation.domain.Stage;
import com.peoplecore.evaluation.dto.*;
import com.peoplecore.evaluation.repository.EvaluationRulesRepository;
import com.peoplecore.evaluation.repository.SeasonRepository;
import com.peoplecore.evaluation.repository.StageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 평가시즌 - 시즌 생성/조회/수정/삭제
@Service
@Transactional
public class SeasonService {

    private final SeasonRepository seasonRepository;
    private final StageRepository stageRepository;
    private final EvaluationRulesService rulesService;
    private final CompanyRepository companyRepository;
    private final EvaluationRulesRepository rulesRepository;

    public SeasonService(SeasonRepository seasonRepository, StageRepository stageRepository, EvaluationRulesService rulesService, CompanyRepository companyRepository, EvaluationRulesRepository rulesRepository) {
        this.seasonRepository = seasonRepository;
        this.stageRepository = stageRepository;
        this.rulesService = rulesService;
        this.companyRepository = companyRepository;
        this.rulesRepository = rulesRepository;
    }

    //  1. 시즌목록
    public List<SeasonResponseDto> getSeasons(UUID companyId) {
        List<SeasonResponseDto> result = new ArrayList<>();
        for (Season season : seasonRepository.findAllByCompany(companyId)) {
            result.add(SeasonResponseDto.from(season));
        }
        return result;
    }

    //    2  활성시즌목록(드롭다운)
    public List<SeasonDropDto> getActiveSeasons(UUID companyId) {
        return seasonRepository.findActiveByCompany(companyId);
    }

    //    3.시즌상세조회
    public SeasonDetailDto getSeasonDetail(UUID companyId, Long seasonId) {
        Season season = seasonRepository.findById(seasonId).orElseThrow(() -> new IllegalArgumentException("시즌을 찿을 수 없습니다"));

        //  회사 소유권 검증
        if (!season.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("접근 권한이 없는 시즌입니다");
        }

        List<Stage> stages = stageRepository.findBySeason_SeasonId(seasonId);
        EvaluationRulesDto rules = rulesService.getBySeasonId(seasonId); //규칙없으면 null

        return SeasonDetailDto.from(season, stages, rules);
    }

    //    4. 시즌생성
//    드롭다운, 날짜 선택형 // +입력받는 동시 5단계 생성(관리)
    public Long createSeason(UUID companyId, Long empid, SeasonCreateRequestDto requestDto) {

        // 기간 유효성 — 시즌 기간
        if (requestDto.getEndDate().isBefore(requestDto.getStartDate())) {
            throw new IllegalArgumentException("종료일이 시작일보다 빠를 수 없습니다");
        }

        // 단계 일정 유효성 — 5개 / 순서 / 시즌 기간 내 포함
        validateStageInputs(requestDto);

        // 회사 확인 (FK 참조용 엔티티 로드)
        Company company = companyRepository.findById(companyId).orElseThrow(() -> new IllegalArgumentException("회사를 찾을 수 없습니다"));

        // 시즌 저장 — 상태 DRAFT 로 시작
        Season season = seasonRepository.save(Season.builder()
                .company(company)
                .name(requestDto.getName())
                .period(requestDto.getPeriod())
                .startDate(requestDto.getStartDate())
                .endDate(requestDto.getEndDate())
                .status(EvalSeasonStatus.DRAFT)
                .build());

//        고정 이름 + 요청받은 날짜로 5단계 생성
        String[] stageNames = {"목표등록", "자기평가", "상위자평가", "등급 산정 및 보정", "결과확정"};
        List<SeasonCreateRequestDto.StageInput> stageInputs = requestDto.getStages();
        for (int i = 0; i < stageNames.length; i++) {
            SeasonCreateRequestDto.StageInput in = stageInputs.get(i);
            stageRepository.save(Stage.builder()
                    .season(season)
                    .name(stageNames[i])
                    .orderNo(i + 1)
                    .startDate(in.getStartDate())
                    .endDate(in.getEndDate())
                    .build());
//            status= Builder.Default로 자동주입
        }

//        규칙 row 자동 생성 — 직전 시즌 복사 or 업계 표준 기본값 (내부 분기)
        rulesService.createInitialRules(season);

        return season.getSeasonId();
    }

    // 단계 일정 검증 — 순서/기간 포함 여부/겹침 방지
    private void validateStageInputs(SeasonCreateRequestDto req) {
        List<SeasonCreateRequestDto.StageInput> stages = req.getStages();
        if (stages == null || stages.size() != 5) {
            throw new IllegalArgumentException("단계 일정 5개가 필요합니다");
        }

        java.time.LocalDate seasonStart = req.getStartDate();
        java.time.LocalDate seasonEnd = req.getEndDate();
        java.time.LocalDate prevEnd = null;

        for (int i = 0; i < stages.size(); i++) {
            SeasonCreateRequestDto.StageInput s = stages.get(i);

            if (s.getStartDate() == null || s.getEndDate() == null) {
                throw new IllegalArgumentException((i + 1) + "번째 단계 날짜가 누락되었습니다");
            }
            if (s.getEndDate().isBefore(s.getStartDate())) {
                throw new IllegalArgumentException((i + 1) + "번째 단계: 종료일이 시작일보다 빠를 수 없습니다");
            }
            if (s.getStartDate().isBefore(seasonStart) || s.getEndDate().isAfter(seasonEnd)) {
                throw new IllegalArgumentException((i + 1) + "번째 단계는 시즌 기간 내여야 합니다");
            }
            if (prevEnd != null && s.getStartDate().isBefore(prevEnd)) {
                throw new IllegalArgumentException((i + 1) + "번째 단계는 이전 단계 이후에 시작해야 합니다");
            }
            prevEnd = s.getEndDate();
        }
    }

    //    5.시즌 수정 -closed는 수정 불가
    public SeasonResponseDto updateSeason(UUID companyId, Long seasonId, SeasonUpdateRequestDto req) {

        // 기간 유효성
        if (req.getEndDate().isBefore(req.getStartDate())) {
            throw new IllegalArgumentException("종료일이 시작일보다 빠를 수 없습니다");
        }

        // 시즌 조회
        Season season = seasonRepository.findById(seasonId).orElseThrow(() -> new IllegalArgumentException("시즌을 찾을 수 없습니다"));
        // 회사 소유권 검증
        if (!season.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("접근 권한이 없는 시즌입니다");
        }
        // 상태 체크 — CLOSED 시즌은 수정 금지
        if (season.getStatus() == EvalSeasonStatus.CLOSED) {
            throw new IllegalStateException("완료된 시즌은 수정할 수 없습니다");
        }
        // 기본정보 갱신 (dirty checking 으로 UPDATE 발행)
        season.updateBasicInfo(req.getName(), req.getPeriod(), req.getStartDate(), req.getEndDate());

        return SeasonResponseDto.from(season);
    }

    //    6. 시즌삭제
    public void deleteSeason(UUID companyId, Long seasonId) {

        // 시즌 조회
        Season season = seasonRepository.findById(seasonId).orElseThrow(() -> new IllegalArgumentException("시즌을 찾을 수 없습니다"));
        // 회사 소유권 검증
        if (!season.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("접근 권한이 없는 시즌입니다");
        }
        // 상태 체크 — DRAFT 만 삭제 허용
        if (season.getStatus() != EvalSeasonStatus.DRAFT) {
            throw new IllegalStateException("진행 중이거나 완료된 시즌은 삭제할 수 없습니다");
        }
        // 연관 데이터 정리 — FK 제약, 부모 삭제 전 자식 먼저 제거
        //  1 단계
        stageRepository.deleteAll(stageRepository.findBySeason_SeasonId(seasonId));
        //  2 평가 규칙 (있을 수도, 없을 수도)
        rulesService.deleteBySeasonId(seasonId);

        // 시즌 본체 삭제
        seasonRepository.delete(season);
    }

}

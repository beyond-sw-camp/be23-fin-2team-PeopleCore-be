package com.peoplecore.evaluation.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.evaluation.domain.EvalGrade;
import com.peoplecore.evaluation.domain.EvalSeasonStatus;
import com.peoplecore.evaluation.domain.EvaluationRules;
import com.peoplecore.evaluation.domain.Season;
import com.peoplecore.evaluation.domain.Stage;
import com.peoplecore.evaluation.domain.StageType;
import com.peoplecore.evaluation.dto.*;
import com.peoplecore.evaluation.repository.EvalGradeRepository;
import com.peoplecore.evaluation.repository.SeasonRepository;
import com.peoplecore.evaluation.repository.StageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final EmployeeRepository employeeRepository;
    private final EvalGradeRepository evalGradeRepository;
    private final ObjectMapper objectMapper;

    public SeasonService(SeasonRepository seasonRepository, StageRepository stageRepository, EvaluationRulesService rulesService, CompanyRepository companyRepository, EmployeeRepository employeeRepository, EvalGradeRepository evalGradeRepository, ObjectMapper objectMapper) {
        this.seasonRepository = seasonRepository;
        this.stageRepository = stageRepository;
        this.rulesService = rulesService;
        this.companyRepository = companyRepository;
        this.employeeRepository = employeeRepository;
        this.evalGradeRepository = evalGradeRepository;
        this.objectMapper = objectMapper;
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
    //    - rules: OPEN 이후면 Season.formSnapshot(박제본) 기준, DRAFT 면 회사 현재 규칙 기준
    public SeasonDetailDto getSeasonDetail(UUID companyId, Long seasonId) {
        Season season = seasonRepository.findById(seasonId).orElseThrow(() -> new IllegalArgumentException("시즌을 찿을 수 없습니다"));

        //  회사 소유권 검증
        if (!season.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("접근 권한이 없는 시즌입니다");
        }

        List<Stage> stages = stageRepository.findBySeason_SeasonId(seasonId);

        // rules 주입: 시즌이 스냅샷을 박제했으면 그걸 파싱, 아니면 회사 현재 규칙
        EvaluationRulesDto rules;
        if (season.getFormSnapshot() != null) {
            rules = EvaluationRulesDto.fromSnapshot(season.getFormSnapshot(), season.getFormVersion(), objectMapper);
        } else {
            rules = rulesService.getByCompanyId(companyId);
        }

        return SeasonDetailDto.from(season, stages, rules);
    }

    //    4. 시즌생성
//    드롭다운, 날짜 선택형 // +입력받는 동시 단계 생성 (회사 규칙 items 기준 동적 개수)
    public Long createSeason(UUID companyId, Long empid, SeasonCreateRequestDto requestDto) {

        // 기간 유효성 — 시즌 기간
        if (requestDto.getEndDate().isBefore(requestDto.getStartDate())) {
            throw new IllegalArgumentException("종료일이 시작일보다 빠를 수 없습니다");
        }

        // 시즌 간 기간 겹침 금지 (같은 회사 내)
        validateNoOverlap(companyId, requestDto.getStartDate(), requestDto.getEndDate(), null);

        // 단계 스펙(이름+타입) 먼저 구성 -> 검증/저장에 재사용
        List<StageSpec> stageSpecs = buildStageSpecs(companyId);
        validateStageInputs(requestDto, stageSpecs.size());

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

//        동적 단계 저장 (name + type + 날짜)
        List<SeasonCreateRequestDto.StageInput> stageInputs = requestDto.getStages();
        for (int i = 0; i < stageSpecs.size(); i++) {
            StageSpec spec = stageSpecs.get(i);
            SeasonCreateRequestDto.StageInput in = stageInputs.get(i);
            stageRepository.save(Stage.builder()
                    .season(season)
                    .name(spec.name())              // EVALUATION 만 값, 나머지 null
                    .type(spec.type())              // 시스템 식별용 타입
                    .orderNo(i + 1)
                    .startDate(in.getStartDate())
                    .endDate(in.getEndDate())
                    .build());
//            status= Builder.Default로 자동주입
        }

//       시즌 OPEN 시 현재 회사 규칙을 스냅샷으로 박제
        return season.getSeasonId();
    }

    // 단계 일정 검증 — 순서/기간 포함 여부/겹침 방지
    private void validateStageInputs(SeasonCreateRequestDto req, int expectedSize) {
        List<SeasonCreateRequestDto.StageInput> stages = req.getStages();
        if (stages == null || stages.size() != expectedSize) {
            throw new IllegalArgumentException("단계 일정 " + expectedSize + "개가 필요합니다");
        }

        java.time.LocalDate seasonStart = req.getStartDate();
        java.time.LocalDate seasonEnd = req.getEndDate();
        java.time.LocalDate prevStart = null;

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
            // 이전 단계보다 반드시 이후여야 함 (같은 날짜 불허)
            if (prevStart != null && !s.getStartDate().isAfter(prevStart)) {
                throw new IllegalArgumentException((i + 1) + "번째 단계 시작일은 이전 단계 시작일보다 이후여야 합니다");
            }
            prevStart = s.getStartDate();
        }

        // 첫 단계 시작일 = 시즌 시작일
        if (!stages.get(0).getStartDate().equals(seasonStart)) {
            throw new IllegalArgumentException("첫 단계 시작일은 시즌 시작일과 같아야 합니다");
        }
        // 끝 단계 종료일 = 시즌 종료일
        if (!stages.get(stages.size() - 1).getEndDate().equals(seasonEnd)) {
            throw new IllegalArgumentException("마지막 단계 종료일은 시즌 종료일과 같아야 합니다");
        }
    }

    // 회사 규칙 기준 단계 스펙 리스트 custom
    //   EVALUATION 만 rules.items 에서 이름 가져옴 (HR이 커스텀한 평가항목명)
    //   locked=true, enabled=false 인 항목은 스킵
    private List<StageSpec> buildStageSpecs(UUID companyId) {
        EvaluationRules rules = rulesService.getEntityByCompanyId(companyId);
        FormSnapshotDto snap;
        try {
            snap = objectMapper.readValue(rules.getFormValues(), FormSnapshotDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("회사 규칙 파싱 실패", e);
        }

        List<StageSpec> specs = new ArrayList<>();
        specs.add(new StageSpec(null, StageType.GOAL_ENTRY));                // 1번
        if (snap.getItemList() != null) {
            for (FormSnapshotDto.Item item : snap.getItemList()) {
                if (Boolean.TRUE.equals(item.getLocked()) && Boolean.FALSE.equals(item.getEnabled())) continue;
                specs.add(new StageSpec(item.getName(), StageType.EVALUATION)); // 2번~
            }
        }
        specs.add(new StageSpec(null, StageType.GRADING));                   // N+2
        specs.add(new StageSpec(null, StageType.FINALIZATION));              // N+3
        return specs;
    }

    // 이름+타입 페어 컬럼명 (EVALUATION 만 name 채움, 고정 3종은 null)
    private record StageSpec(String name, StageType type) {}



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
        // 시즌 간 기간 겹침 금지 (자기 자신 제외)
        validateNoOverlap(companyId, req.getStartDate(), req.getEndDate(), seasonId);

        // 기본정보 갱신 (dirty checking 으로 UPDATE 발행)
        season.updateBasicInfo(req.getName(), req.getPeriod(), req.getStartDate(), req.getEndDate());

        return SeasonResponseDto.from(season);
    }

    // 시즌 간 기간 겹침 검증 — 같은 회사 다른 시즌과 날짜 범위가 겹치면 예외
    private void validateNoOverlap(UUID companyId, java.time.LocalDate newStart, java.time.LocalDate newEnd, Long excludeSeasonId) {
        List<Season> overlapping = seasonRepository.findOverlapping(companyId, newStart, newEnd, excludeSeasonId);
        if (!overlapping.isEmpty()) {
            Season first = overlapping.get(0);
            throw new IllegalArgumentException(
                    String.format("다른 시즌(%s: %s ~ %s)과 기간이 겹칩니다",
                            first.getName(), first.getStartDate(), first.getEndDate()));
        }
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

        // 시즌 본체 삭제
        seasonRepository.delete(season);
    }

    //    6. 시즌 오픈 (DRAFT → OPEN)
//     - 상태 전이 + 규칙 스냅샷 동결 + 전 사원 EvalGrade row 일괄 생성
//     - 스케줄러 또는 수동 호출 진입점. DRAFT 가 아니면 멱등 스킵
    public void openSeason(Long seasonId) {
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new IllegalArgumentException("시즌을 찾을 수 없습니다"));

        // 멱등 — 이미 OPEN/CLOSED 면 아무것도 하지 않음
        if (season.getStatus() != EvalSeasonStatus.DRAFT) {
            return;
        }

        // 1) 상태 전이
        season.open();

        UUID companyId = season.getCompany().getCompanyId();

        // 2) 회사 규칙(하드 컬럼 + formValues JSON) 을 병합 스냅샷 JSON 으로 빌드 -> Season.formSnapshot 박제
        EvaluationRules rules = rulesService.getEntityByCompanyId(companyId);
        String mergedJson = rulesService.buildMergedSnapshotJson(rules);
        season.freezeSnapshot(mergedJson, rules.getFormVersion());

        // 3) 전 사원 EvalGrade row 일괄 INSERT — 점수/등급 컬럼은 NULL 로 시작
        //    dept/title 은 스냅샷 컬럼에 박제 (이후 조직개편/이동해도 그 시즌은 고정)
        List<Employee> employees = employeeRepository.findActiveEmployeesWithDeptAndGrade(companyId);

        List<EvalGrade> rows = new ArrayList<>();
        for (Employee emp : employees) {
            EvalGrade row = EvalGrade.builder()
                    .emp(emp)
                    .season(season) //이번에 오픈되는 fk
                    .isCalibrated(false) //보정이력 초기화
                    .deptIdSnapshot(emp.getDept() != null ? emp.getDept().getDeptId() : null) //시즌 오픈시 부서id 박제
                    .deptNameSnapshot(emp.getDept() != null ? emp.getDept().getDeptName() : null) //시즌 오픈시 부서명 박제
                    .positionSnapshot(emp.getTitle() != null ? emp.getTitle().getTitleName() : null) //직급명 박제
                    .build();
//            점수/등급등은 모두 null로 시작
            rows.add(row);
        }
        evalGradeRepository.saveAll(rows);
    }


}

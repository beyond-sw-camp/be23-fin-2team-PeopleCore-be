package com.peoplecore.evaluation.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.evaluation.domain.EmpEvaluatorGlobal;
import com.peoplecore.evaluation.domain.EvalGrade;
import com.peoplecore.evaluation.domain.EvalSeasonStatus;
import com.peoplecore.evaluation.domain.EvaluationRules;
import com.peoplecore.evaluation.domain.Season;
import com.peoplecore.evaluation.domain.Stage;
import com.peoplecore.evaluation.domain.StageType;
import com.peoplecore.evaluation.dto.*;
import com.peoplecore.evaluation.repository.EmpEvaluatorGlobalRepository;
import com.peoplecore.evaluation.repository.EvalGradeRepository;
import com.peoplecore.evaluation.repository.SeasonRepository;
import com.peoplecore.evaluation.repository.StageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final EmpEvaluatorGlobalRepository empEvaluatorGlobalRepository;
    private final com.peoplecore.alarm.publisher.HrAlarmPublisher hrAlarmPublisher;
    private final ObjectMapper objectMapper;

    public SeasonService(SeasonRepository seasonRepository, StageRepository stageRepository, EvaluationRulesService rulesService, CompanyRepository companyRepository, EmployeeRepository employeeRepository, EvalGradeRepository evalGradeRepository, EmpEvaluatorGlobalRepository empEvaluatorGlobalRepository, com.peoplecore.alarm.publisher.HrAlarmPublisher hrAlarmPublisher, ObjectMapper objectMapper) {
        this.seasonRepository = seasonRepository;
        this.stageRepository = stageRepository;
        this.rulesService = rulesService;
        this.companyRepository = companyRepository;
        this.employeeRepository = employeeRepository;
        this.evalGradeRepository = evalGradeRepository;
        this.empEvaluatorGlobalRepository = empEvaluatorGlobalRepository;
        this.hrAlarmPublisher = hrAlarmPublisher;
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

    //    2-1. 현재 진행 시즌 상세 (회사당 1개) — 단계 게이트(목표등록/자기평가 등) 화면용
    //    OPEN 시즌이 없으면 null 반환. DRAFT 시즌은 무시 (게이트는 진행중 시즌만 본다)
    public SeasonDetailDto getCurrentSeasonDetail(UUID companyId) {
        java.util.Optional<Season> opt = seasonRepository.findByCompany_CompanyIdAndStatus(companyId, EvalSeasonStatus.OPEN);
        if (opt.isEmpty()) return null;
        return getSeasonDetail(companyId, opt.get().getSeasonId());
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

        // 평가자 매핑 가드 — 그 시점 active 사원 중 매핑/제외 결정 안 된 사람 있으면 차단 (HR 즉시 인지)
        // (DRAFT~OPEN 사이 신규 입사자는 OPEN 시 미지정 처리 + 알림 발송)
        List<Employee> activeEmployees = employeeRepository.findEvalTargetsByCompany(companyId);
        Set<Long> mappedEmpIds = new HashSet<>();
        for (EmpEvaluatorGlobal m : empEvaluatorGlobalRepository.findByCompanyId(companyId)) {
            mappedEmpIds.add(m.getEvaluatee().getEmpId());
        }
        List<String> undecided = new ArrayList<>();
        for (Employee emp : activeEmployees) {
            if (!mappedEmpIds.contains(emp.getEmpId())) {
                undecided.add(emp.getEmpName() + "(" + emp.getEmpNum() + ")");
            }
        }
        if (!undecided.isEmpty()) {
            throw new IllegalArgumentException(
                "매핑/제외 결정이 안 된 사원이 있어 시즌을 만들 수 없습니다: " + String.join(", ", undecided));
        }

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
            // 이전 단계 종료일 이후로만 다음 단계 시작 가능 (같은 날짜 불허)
            if (prevEnd != null && !s.getStartDate().isAfter(prevEnd)) {
                throw new IllegalArgumentException((i + 1) + "번째 단계 시작일은 이전 단계 종료일 이후여야 합니다");
            }
            prevEnd = s.getEndDate();
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
    //    - req.stages 동봉 시: 시즌 + 단계를 한 트랜잭션에서 원자 업데이트, 새 상태 기준 검증
    //    - 미동봉 시: 기존 DB 단계 그대로 새 시즌 범위와 정합성 검증
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

        // 단계 동봉 — 새 상태로 통합 검증 후 일괄 dirty-update
        if (req.getStages() != null && !req.getStages().isEmpty()) {
            applyAndValidateStages(seasonId, req);
        } else {
            // 단계 미동봉 — DB 단계와 새 시즌 범위 정합성만 검증
            validateStagesAgainstSeasonRange(seasonId, req.getStartDate(), req.getEndDate());
        }

        // 기본정보 갱신 (dirty checking 으로 UPDATE 발행)
        season.updateBasicInfo(req.getName(), req.getPeriod(), req.getStartDate(), req.getEndDate());

        return SeasonResponseDto.from(season);
    }

    // 시즌 + 단계 원자 업데이트 — 화면이 보낸 새 상태 기준으로 검증 후 dirty-update
    private void applyAndValidateStages(Long seasonId, SeasonUpdateRequestDto req) {
        // DB 단계를 orderNo 오름차순으로 정렬 (스트림/람다 미사용)
        List<Stage> stages = new ArrayList<>(stageRepository.findBySeason_SeasonId(seasonId));
        for (int i = 0; i < stages.size(); i++) {
            for (int j = i + 1; j < stages.size(); j++) {
                Integer oi = stages.get(i).getOrderNo();
                Integer oj = stages.get(j).getOrderNo();
                int vi = (oi == null) ? 0 : oi;
                int vj = (oj == null) ? 0 : oj;
                if (vi > vj) {
                    Stage tmp = stages.get(i);
                    stages.set(i, stages.get(j));
                    stages.set(j, tmp);
                }
            }
        }

        if (req.getStages().size() != stages.size()) {
            throw new IllegalArgumentException("단계 수가 맞지 않습니다");
        }

        // 입력을 stageId 로 인덱싱
        java.util.Map<Long, SeasonUpdateRequestDto.StageInput> byId = new java.util.HashMap<>();
        for (SeasonUpdateRequestDto.StageInput in : req.getStages()) {
            byId.put(in.getStageId(), in);
        }

        java.time.LocalDate seasonStart = req.getStartDate();
        java.time.LocalDate seasonEnd = req.getEndDate();
        java.time.LocalDate prevEnd = null;

        for (int i = 0; i < stages.size(); i++) {
            Stage stage = stages.get(i);
            SeasonUpdateRequestDto.StageInput in = byId.get(stage.getStageId());
            if (in == null) {
                throw new IllegalArgumentException((i + 1) + "번째 단계 입력이 누락되었습니다");
            }
            java.time.LocalDate s = in.getStartDate();
            java.time.LocalDate e = in.getEndDate();

            if (e.isBefore(s)) {
                throw new IllegalArgumentException((i + 1) + "번째 단계: 종료일이 시작일보다 빠를 수 없습니다");
            }
            if (s.isBefore(seasonStart) || e.isAfter(seasonEnd)) {
                throw new IllegalArgumentException((i + 1) + "번째 단계는 시즌 기간 내여야 합니다");
            }
            if (prevEnd != null && !s.isAfter(prevEnd)) {
                throw new IllegalArgumentException((i + 1) + "번째 단계 시작일은 이전 단계 종료일 이후여야 합니다");
            }
            prevEnd = e;
        }

        // 첫 단계 시작일 = 시즌 시작일, 마지막 단계 종료일 = 시즌 종료일 (생성 시 invariant 유지)
        SeasonUpdateRequestDto.StageInput firstIn = byId.get(stages.get(0).getStageId());
        SeasonUpdateRequestDto.StageInput lastIn = byId.get(stages.get(stages.size() - 1).getStageId());
        if (!firstIn.getStartDate().equals(seasonStart)) {
            throw new IllegalArgumentException("첫 단계 시작일은 시즌 시작일과 같아야 합니다");
        }
        if (!lastIn.getEndDate().equals(seasonEnd)) {
            throw new IllegalArgumentException("마지막 단계 종료일은 시즌 종료일과 같아야 합니다");
        }

        // 검증 통과 — dirty-update
        for (Stage stage : stages) {
            SeasonUpdateRequestDto.StageInput in = byId.get(stage.getStageId());
            stage.updateDates(in.getStartDate(), in.getEndDate());
        }
    }

    // 시즌 수정 시 기존 단계 일정과 새 날짜 범위 정합성 검증
    private void validateStagesAgainstSeasonRange(Long seasonId, java.time.LocalDate newStart, java.time.LocalDate newEnd) {
        List<Stage> stages = stageRepository.findBySeason_SeasonId(seasonId);
        if (stages.isEmpty()) return;
        stages.sort(java.util.Comparator.comparing(s -> s.getOrderNo() == null ? 0 : s.getOrderNo()));

        Stage first = stages.get(0);
        Stage last = stages.get(stages.size() - 1);

        if (first.getStartDate() != null && !first.getStartDate().equals(newStart)) {
            throw new IllegalArgumentException("첫 단계 시작일(" + first.getStartDate() + ")과 시즌 시작일이 다릅니다. 단계 일정부터 조정하세요");
        }
        if (last.getEndDate() != null && !last.getEndDate().equals(newEnd)) {
            throw new IllegalArgumentException("마지막 단계 종료일(" + last.getEndDate() + ")과 시즌 종료일이 다릅니다. 단계 일정부터 조정하세요");
        }
        for (Stage s : stages) {
            if (s.getStartDate() != null && s.getStartDate().isBefore(newStart)) {
                throw new IllegalArgumentException("단계 시작일이 시즌 기간을 벗어납니다. 단계 일정부터 조정하세요");
            }
            if (s.getEndDate() != null && s.getEndDate().isAfter(newEnd)) {
                throw new IllegalArgumentException("단계 종료일이 시즌 기간을 벗어납니다. 단계 일정부터 조정하세요");
            }
        }
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

        // 3) 평가 대상자 EvalGrade row 일괄 INSERT — 점수/등급 컬럼은 NULL 로 시작
        //    재직중(ACTIVE) + 일반 사원(EMPLOYEE)
        //    dept/title/평가자 는 스냅샷 컬럼에 박제
        List<Employee> employees = employeeRepository.findEvalTargetsByCompany(companyId);

        // 글로벌 매핑 미리 로드 (한 번에) — evaluatee_emp_id → mapping
        Map<Long, EmpEvaluatorGlobal> mappingByEmp = new HashMap<>();
        for (EmpEvaluatorGlobal m : empEvaluatorGlobalRepository.findByCompanyId(companyId)) {
            mappingByEmp.put(m.getEvaluatee().getEmpId(), m);
        }

        // EvalGrade 박제 — 매핑 있으면 평가자 박제, 없으면 미지정(null), 평가 제외는 row skip.
        // 창립 가드를 통과했어도 DRAFT~OPEN 사이 신규 입사자가 미지정 상태로 들어올 수 있음 → 알림으로 처리.
        int unmappedCount = 0;
        List<EvalGrade> rows = new ArrayList<>();
        for (Employee emp : employees) {
            EmpEvaluatorGlobal mapping = mappingByEmp.get(emp.getEmpId());

            // 평가 제외 명시된 사원은 그 시즌 평가 대상 X (row 자체 안 만듦)
            if (mapping != null && mapping.isExcluded()) {
                continue;
            }

            Long evId = null;
            String evName = null;
            if (mapping != null && mapping.getEvaluator() != null) {
                evId = mapping.getEvaluator().getEmpId();
                evName = mapping.getEvaluator().getEmpName();
            } else {
                // 매핑 없음 — 미지정 상태로 박제 (HR이 OPEN 후 사후 처리)
                unmappedCount++;
            }

            EvalGrade row = EvalGrade.builder()
                    .emp(emp)
                    .season(season)
                    .isCalibrated(false)
                    .deptIdSnapshot(emp.getDept() != null ? emp.getDept().getDeptId() : null)
                    .deptNameSnapshot(emp.getDept() != null ? emp.getDept().getDeptName() : null)
                    .positionSnapshot(emp.getTitle() != null ? emp.getTitle().getTitleName() : null)
                    .evaluatorIdSnapshot(evId)        // null 가능
                    .evaluatorNameSnapshot(evName)
                    .build();
            rows.add(row);
        }
        evalGradeRepository.saveAll(rows);

        // 미지정자 있으면 HR 관리자에게 알림 발송 (DRAFT~OPEN 사이 신규 입사자 등)
        if (unmappedCount > 0) {
            notifyUnmappedAtOpen(season, unmappedCount);
        }
    }

    // OPEN 시 미지정자 발생 알림 — HR_ADMIN/SUPER_ADMIN 에게 발송
    private void notifyUnmappedAtOpen(Season season, int unmappedCount) {
        UUID companyId = season.getCompany().getCompanyId();

        // HR 관리자 empId 수집
        List<com.peoplecore.employee.domain.EmpRole> hrRoles = new ArrayList<>();
        hrRoles.add(com.peoplecore.employee.domain.EmpRole.HR_ADMIN);
        hrRoles.add(com.peoplecore.employee.domain.EmpRole.HR_SUPER_ADMIN);
        List<Employee> hrAdmins = employeeRepository
            .findByCompany_CompanyIdAndEmpRoleIn(companyId, hrRoles);
        List<Long> hrAdminEmpIds = new ArrayList<>();
        for (Employee admin : hrAdmins) {
            hrAdminEmpIds.add(admin.getEmpId());
        }
        if (hrAdminEmpIds.isEmpty()) return;

        hrAlarmPublisher.publisher(com.peoplecore.event.AlarmEvent.builder()
            .companyId(companyId)
            .alarmType("HR")
            .alarmTitle("평가자 지정 필요")
            .alarmContent(season.getName() + " 시즌 시작 — 평가자가 지정되지 않은 사원 "
                + unmappedCount + "명이 있습니다.")
            .alarmLink("/eval-admin?tab=emp-evaluator")
            .alarmRefType("EVAL_SEASON")
            .alarmRefId(season.getSeasonId())
            .empIds(hrAdminEmpIds)
            .build());
    }


}

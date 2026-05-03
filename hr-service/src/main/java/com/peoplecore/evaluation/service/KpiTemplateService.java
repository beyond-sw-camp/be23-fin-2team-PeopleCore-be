package com.peoplecore.evaluation.service;

import com.peoplecore.department.domain.Department;
import com.peoplecore.department.repository.DepartmentRepository;
import com.peoplecore.evaluation.domain.EvalSeasonStatus;
import com.peoplecore.evaluation.domain.KpiOption;
import com.peoplecore.evaluation.domain.KpiOptionType;
import com.peoplecore.evaluation.domain.KpiTemplate;
import com.peoplecore.evaluation.domain.Season;
import com.peoplecore.evaluation.domain.Stage;
import com.peoplecore.evaluation.domain.StageStatus;
import com.peoplecore.evaluation.domain.StageType;
import com.peoplecore.evaluation.dto.KpiTemplateRequest;
import com.peoplecore.evaluation.dto.KpiTemplateResponse;
import com.peoplecore.evaluation.repository.GoalRepository;
import com.peoplecore.evaluation.repository.KpiOptionRepository;
import com.peoplecore.evaluation.repository.KpiTemplateRepository;
import com.peoplecore.evaluation.repository.SeasonRepository;
import com.peoplecore.evaluation.repository.SelfEvaluationRepository;
import com.peoplecore.evaluation.repository.StageRepository;
import com.peoplecore.grade.domain.Grade;
import com.peoplecore.grade.repository.GradeRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// KPI지표 템플릿 - 부서/카테고리별 지표 관리
@Service
@Transactional
public class KpiTemplateService {

    private final KpiTemplateRepository kpiTemplateRepository;
    private final KpiOptionRepository kpiOptionRepository;
    private final DepartmentRepository departmentRepository;
    private final GradeRepository gradeRepository;
    private final GoalRepository goalRepository;
    private final SeasonRepository seasonRepository;
    private final StageRepository stageRepository;
    private final SelfEvaluationRepository selfEvaluationRepository;

    public KpiTemplateService(KpiTemplateRepository kpiTemplateRepository,
                              KpiOptionRepository kpiOptionRepository,
                              DepartmentRepository departmentRepository,
                              GradeRepository gradeRepository,
                              GoalRepository goalRepository,
                              SeasonRepository seasonRepository,
                              StageRepository stageRepository,
                              SelfEvaluationRepository selfEvaluationRepository) {
        this.kpiTemplateRepository = kpiTemplateRepository;
        this.kpiOptionRepository = kpiOptionRepository;
        this.departmentRepository = departmentRepository;
        this.gradeRepository = gradeRepository;
        this.goalRepository = goalRepository;
        this.seasonRepository = seasonRepository;
        this.stageRepository = stageRepository;
        this.selfEvaluationRepository = selfEvaluationRepository;
    }

    // 직급 조회 — null 허용 (전 직급 공통). 값 있을 때만 회사 스코프 검증
    private Grade resolveGrade(UUID companyId, Long gradeId) {
        if (gradeId == null) return null;
        return gradeRepository.findByGradeIdAndCompanyId(gradeId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("직급을 찾을 수 없습니다"));
    }

    // 회사의 OPEN 시즌에서 GOAL_ENTRY 단계가 IN_PROGRESS 면 KPI 템플릿 변경 차단
    // — 박제 시점 보호: 사원이 등록 중인 Goal 의 박제값이 시점에 따라 달라지는 사고 방지
    private void requireGoalEntryNotInProgress(UUID companyId) {
        Season openSeason = seasonRepository.findByCompany_CompanyIdAndStatus(companyId, EvalSeasonStatus.OPEN).orElse(null);
        if (openSeason == null) return; // OPEN 시즌 없음 — 자유
        Stage goalEntry = stageRepository.findBySeason_SeasonIdAndType(openSeason.getSeasonId(), StageType.GOAL_ENTRY).orElse(null);
        if (goalEntry == null) return;
        if (goalEntry.getStatus() == StageStatus.IN_PROGRESS) {
            throw new IllegalStateException("목표 등록 단계 진행 중에는 KPI 지표를 변경할 수 없습니다");
        }
    }

    // 1번 - 부서 IN 필터 없음, 회사 + 부서/직급/카테고리/키워드 필터만
    // 직급 필터: 선택 시 (해당 직급 OR 전 직급 공통) 모두 노출
    // 사내평균(baseline) — 항상 동적 계산:
    //   yearFrom/yearTo 지정 시 그 범위, 미지정 시 전체 기간
    //   시즌 endDate 기준 + APPROVED 자기평가 actualValue 평균
    public Page<KpiTemplateResponse> getTemplates(UUID companyId, Long deptId, Long gradeId,
                                                  String category, String keyword,
                                                  Integer yearFrom, Integer yearTo,
                                                  Pageable pageable){
        Page<KpiTemplateResponse> page = kpiTemplateRepository.searchTemplates(
                companyId, deptId, gradeId, category, keyword, pageable);

        if (!page.getContent().isEmpty()) {
            LocalDate from = LocalDate.of(yearFrom != null ? yearFrom : 1900, 1, 1);
            LocalDate to   = LocalDate.of(yearTo   != null ? yearTo   : 9999, 12, 31);

            List<Long> kpiIds = new ArrayList<>();
            for (KpiTemplateResponse r : page.getContent()) {
                kpiIds.add(r.getKpiId());
            }

            List<SelfEvaluationRepository.KpiAvgRow> rows =
                    selfEvaluationRepository.averageByKpiInRange(kpiIds, from, to);
            Map<Long, BigDecimal> avgMap = new HashMap<>();
            for (SelfEvaluationRepository.KpiAvgRow row : rows) {
                avgMap.put(row.getKpiId(), row.getAvg());
            }

            for (KpiTemplateResponse r : page.getContent()) {
                r.setBaseline(avgMap.get(r.getKpiId()));
            }
        }
        return page;
    }

    // 2번 단건 조회
    public KpiTemplateResponse getTemplate(UUID companyId, Long id) {
        KpiTemplate t = kpiTemplateRepository.findOneByCompany(id, companyId).orElse(null);
        if (t == null) {
            throw new IllegalArgumentException("KPI 지표를 찾을 수 없습니다: " + id);
        }
        return KpiTemplateResponse.from(t);
    }

//    3번 신규등록
    public KpiTemplateResponse createTemplate(UUID companyId, KpiTemplateRequest req){

        // 목표 등록 단계 진행 중에는 신규 등록 차단 (사원 폼별로 보이는 옵션이 달라지는 것 방지)
        requireGoalEntryNotInProgress(companyId);

        Department department = departmentRepository.findById(req.getDeptId()).orElse(null);
        if(department == null){
            throw new IllegalArgumentException("부서를 찾을 수 없습니다");
        }
        if(!department.getCompany().getCompanyId().equals(companyId)){
            throw new IllegalArgumentException("다른 회사 부서는 사용할 수 없습니다");
        }

//       카테고리 옵션 로드, active + type + 회사 검증
        KpiOption category = kpiOptionRepository.findById(req.getCategoryOptionId()).orElse(null);
        if (category == null) {
            throw new IllegalArgumentException("카테고리를 찾을 수 없습니다");
        }
        if (!Boolean.TRUE.equals(category.getIsActive()) || category.getType() != KpiOptionType.CATEGORY || !category.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("사용할 수 없는 카테고리입니다");
        }

//        단위 옵션 로드, active + type + 회사 검증
        KpiOption unit = kpiOptionRepository.findById(req.getUnitOptionId()).orElse(null);
        if (unit == null) {
            throw new IllegalArgumentException("단위를 찾을 수 없습니다");
        }
        if (!Boolean.TRUE.equals(unit.getIsActive()) || unit.getType() != KpiOptionType.UNIT || !unit.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("사용할 수 없는 단위입니다");
        }

        // 직급 — null 이면 해당 부서 전 직급 공통 KPI
        Grade grade = resolveGrade(companyId, req.getGradeId());

//        entity build+ save (baseline 은 NULL 유지 - 시즌 마감 시 자동 집계)
        KpiTemplate t = KpiTemplate.builder()
                .department(department)
                .grade(grade)
                .category(category)
                .unit(unit)
                .name(req.getName())
                .description(req.getDescription())
                .direction(req.getDirection())
                .build();

        KpiTemplate saved = kpiTemplateRepository.save(t);
        return KpiTemplateResponse.from(saved);
    }

    // 4번 수정 - 회사 검증 + 도메인 메서드로 갱신
    public KpiTemplateResponse updateTemplate(UUID companyId, Long id, KpiTemplateRequest req) {

        // 목표 등록 단계 진행 중에는 변경 차단 (박제 시점 보호)
        requireGoalEntryNotInProgress(companyId);

        // 기존 row 로드 (회사 스코프 강제)
        KpiTemplate t = kpiTemplateRepository.findOneByCompany(id, companyId).orElse(null);
        if (t == null) {
            throw new IllegalArgumentException("KPI 지표를 찾을 수 없습니다: " + id);
        }

        // 부서 - null + 회사 검증
        Department department = departmentRepository.findById(req.getDeptId()).orElse(null);
        if (department == null) {
            throw new IllegalArgumentException("부서를 찾을 수 없습니다");
        }
        if (!department.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("다른 회사 부서는 사용할 수 없습니다");
        }

        // 카테고리 - null + 회사 검증
        KpiOption category = kpiOptionRepository.findById(req.getCategoryOptionId()).orElse(null);
        if (category == null) {
            throw new IllegalArgumentException("카테고리를 찾을 수 없습니다");
        }
        if (!category.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("다른 회사 카테고리는 사용할 수 없습니다");
        }

        // 단위 - null + 회사 검증
        KpiOption unit = kpiOptionRepository.findById(req.getUnitOptionId()).orElse(null);
        if (unit == null) {
            throw new IllegalArgumentException("단위를 찾을 수 없습니다");
        }
        if (!unit.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("다른 회사 단위는 사용할 수 없습니다");
        }

        // 직급 — null 이면 해당 부서 전 직급 공통 KPI
        Grade grade = resolveGrade(companyId, req.getGradeId());

        // 도메인 메서드로 일괄 갱신 (dirty checking 으로 자동 UPDATE)
        t.update(department, grade, category, unit, req.getName(), req.getDescription(), req.getDirection());

        return KpiTemplateResponse.from(t);
    }

    // 5번 삭제 - hybrid: 사용 이력 0건이면 hard delete, 있으면 soft delete
    public void deleteTemplate(UUID companyId, Long id) {

        // 목표 등록 단계 진행 중에는 삭제 차단 (사원 등록 중 옵션 사라지는 것 방지)
        requireGoalEntryNotInProgress(companyId);

        // 기존 row 로드 (회사 스코프 강제)
        KpiTemplate t = kpiTemplateRepository.findOneByCompany(id, companyId).orElse(null);
        if (t == null) {
            throw new IllegalArgumentException("KPI 지표를 찾을 수 없습니다: " + id);
        }

        // 사용 이력 카운트 (모든 시즌 Goal 통틀어)
        long usedCount = goalRepository.countByKpiTemplate_KpiId(id);

        if (usedCount == 0) {
            // 한 번도 안 쓰임 -> 물리 삭제
            kpiTemplateRepository.delete(t);
        } else {
            // 한 번이라도 쓴 적 있음 -> 소프트 삭제 (과거 평가 이력 보호)
            t.deactivate();
            // dirty checking 으로 자동 UPDATE
        }
    }
}

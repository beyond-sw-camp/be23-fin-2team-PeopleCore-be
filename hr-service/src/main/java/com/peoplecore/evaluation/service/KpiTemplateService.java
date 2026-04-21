package com.peoplecore.evaluation.service;

import com.peoplecore.department.domain.Department;
import com.peoplecore.department.repository.DepartmentRepository;
import com.peoplecore.evaluation.domain.KpiOption;
import com.peoplecore.evaluation.domain.KpiOptionType;
import com.peoplecore.evaluation.domain.KpiTemplate;
import com.peoplecore.evaluation.dto.KpiTemplateRequest;
import com.peoplecore.evaluation.dto.KpiTemplateResponse;
import com.peoplecore.evaluation.repository.GoalRepository;
import com.peoplecore.evaluation.repository.KpiOptionRepository;
import com.peoplecore.evaluation.repository.KpiTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

// KPI지표 템플릿 - 부서/카테고리별 지표 관리
@Service
@Transactional
public class KpiTemplateService {

    private final KpiTemplateRepository kpiTemplateRepository;
    private final KpiOptionRepository kpiOptionRepository;
    private final DepartmentRepository departmentRepository;
    private final DepartmentDepthResolver departmentDepthResolver;
    private final GoalRepository goalRepository;

    public KpiTemplateService(KpiTemplateRepository kpiTemplateRepository,
                              KpiOptionRepository kpiOptionRepository,
                              DepartmentRepository departmentRepository,
                              DepartmentDepthResolver departmentDepthResolver,
                              GoalRepository goalRepository) {
        this.kpiTemplateRepository = kpiTemplateRepository;
        this.kpiOptionRepository = kpiOptionRepository;
        this.departmentRepository = departmentRepository;
        this.departmentDepthResolver = departmentDepthResolver;
        this.goalRepository = goalRepository;
    }

    // 1번 - depth 정책 기반 부서 IN 필터 적용
    public Page<KpiTemplateResponse> getTemplates(UUID companyId, Long deptId, String category, String keyword, Pageable pageable){
        Set<Long> validDeptIds = departmentDepthResolver.resolveValidDeptIds(companyId);   // ★ 추가
        return kpiTemplateRepository.searchTemplates(companyId, deptId, category, keyword, validDeptIds, pageable);
    }

    // 2번 단건 조회
    public KpiTemplateResponse getTemplate(UUID companyId, Long id) {
        KpiTemplate t = kpiTemplateRepository.findOneByCompany(id, companyId).orElse(null);
        if (t == null) {
            throw new IllegalArgumentException("KPI 지표를 찾을 수 없습니다: " + id);
        }
        return KpiTemplateResponse.from(t);
    }

//    3번 신규등록 -DepartmentDepthResolver호출
    public KpiTemplateResponse createTemplate(UUID companyId, KpiTemplateRequest req){

        // depth 정책 검증
        Set<Long> validDeptIds = departmentDepthResolver.resolveValidDeptIds(companyId);
        if (!validDeptIds.contains(req.getDeptId())) {
            throw new IllegalArgumentException("현재 KPI depth 정책에 맞지 않는 부서입니다: " + req.getDeptId());
        }

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

//        entity build+ save (baseline 은 NULL 유지 - 시즌 마감 시 자동 집계)
        KpiTemplate t = KpiTemplate.builder()
                .department(department)
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

        // 도메인 메서드로 일괄 갱신 (dirty checking 으로 자동 UPDATE)
        t.update(department, category, unit, req.getName(), req.getDescription(), req.getDirection());

        return KpiTemplateResponse.from(t);
    }

    // 5번 삭제 - hybrid: 사용 이력 0건이면 hard delete, 있으면 soft delete
    public void deleteTemplate(UUID companyId, Long id) {

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

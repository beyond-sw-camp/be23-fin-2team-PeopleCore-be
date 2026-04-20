package com.peoplecore.evaluation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.company.domain.Company;
import com.peoplecore.evaluation.domain.EvaluationRules;
import com.peoplecore.evaluation.dto.EvaluationRulesDto;
import com.peoplecore.evaluation.dto.EvaluationRulesSaveRequestDto;
import com.peoplecore.evaluation.repository.EvaluationRulesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 평가규칙 - 회사별 전사 공통 규칙 (Company 와 1:1, 시즌과 무관하게 자유 편집)
// 시즌 OPEN 시 buildMergedSnapshotJson 으로 하드컬럼+JSON 병합본 생성 → Season.formSnapshot 박제
@Service
@Transactional
public class EvaluationRulesService {
    private final EvaluationRulesRepository rulesRepository;
    private final ObjectMapper objectMapper;


    public EvaluationRulesService(EvaluationRulesRepository rulesRepository,
                                  ObjectMapper objectMapper) {
        this.rulesRepository = rulesRepository;
        this.objectMapper = objectMapper;
    }

    //    회사 규칙 조회 — 회사 생성 시 createDefaultRules 로 세팅되어 항상 존재
    @Transactional(readOnly = true)
    public EvaluationRulesDto getByCompanyId(UUID companyId) {
        EvaluationRules rules = rulesRepository.findByCompany_CompanyId(companyId).orElse(null);
        return EvaluationRulesDto.from(rules, objectMapper);
    }

    //    회사 규칙 수정 — 시즌 상태와 무관하게 항상 편집 가능
    //    규칙 수정 시 버전 ++. 다음 시즌 OPEN 때 그 시점 값이 Season.formSnapshot 으로 박제됨
    public EvaluationRulesDto save(UUID companyId, EvaluationRulesSaveRequestDto req) {
        EvaluationRules rules = rulesRepository.findByCompany_CompanyId(companyId)
                .orElseThrow(() -> new IllegalStateException("회사 규칙이 초기화되지 않았습니다"));

        String formValuesJson = serializeFormValues(req);
        EvaluationRulesDto.TaskGradeWeight tgw = req.getTaskGradeWeight();

        // null 오면 기존값 유지
        rules.updateRules(
                tgw != null ? tgw.get상() : rules.getTaskWeightSang(),
                tgw != null ? tgw.get중() : rules.getTaskWeightJung(),
                tgw != null ? tgw.get하() : rules.getTaskWeightHa(),
                req.getUseBiasAdjustment() != null ? req.getUseBiasAdjustment() : rules.getUseBiasAdjustment(),
                req.getBiasWeight() != null ? req.getBiasWeight() : rules.getBiasWeight(),
                req.getMinTeamSize() != null ? req.getMinTeamSize() : rules.getMinTeamSize(),
                formValuesJson
        );

        return EvaluationRulesDto.from(rules, objectMapper);
    }

    //    회사 생성 시 기본 규칙 row 1건 INSERT (CompanyService.createCompany 에서 호출)
    //    업계 표준 기본값으로 채움 (하드 컬럼은 엔티티 @Builder.Default 가 처리: 3/2/1/true/1.0/5)
    public EvaluationRules createDefaultRules(Company company) {
        EvaluationRules rules = EvaluationRules.builder()
                .company(company)
                .formValues(buildDefaultFormValues())
                .build();
        return rulesRepository.save(rules);
    }

    //    회사 규칙 조회 엔티티 (시즌 OPEN 시 스냅샷 박제용)
    @Transactional(readOnly = true)
    public EvaluationRules getEntityByCompanyId(UUID companyId) {
        return rulesRepository.findByCompany_CompanyId(companyId).orElseThrow(() -> new IllegalStateException("회사 규칙이 초기화되지 않았습니다"));
    }

    //    시즌 OPEN 시 박제용 병합 스냅샷 JSON 생성
    //    formValues(JSON 섹션) + 하드 컬럼 6개를 하나의 JSON 오브젝트로 합쳐 반환
    //    -> Season.formSnapshot 에 저장되어 산정/보정 로직은 이 JSON 만 참조
    public String buildMergedSnapshotJson(EvaluationRules rules) {
        Map<String, Object> merged = new LinkedHashMap<>();

        // formValues JSON 섹션 파싱해서 펼쳐 넣기
        if (rules.getFormValues() != null && !rules.getFormValues().isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> formSections = objectMapper.readValue(rules.getFormValues(), Map.class);
                if (formSections != null) merged.putAll(formSections);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("회사 규칙 formValues 파싱 실패", e);
            }
        }

        // 하드 컬럼 6개 박제
        merged.put("taskWeightSang", rules.getTaskWeightSang());
        merged.put("taskWeightJung", rules.getTaskWeightJung());
        merged.put("taskWeightHa", rules.getTaskWeightHa());
        merged.put("useBiasAdjustment", rules.getUseBiasAdjustment());
        merged.put("biasWeight", rules.getBiasWeight());
        merged.put("minTeamSize", rules.getMinTeamSize());

        try {
            return objectMapper.writeValueAsString(merged);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("스냅샷 JSON 직렬화 실패", e);
        }
    }

    //    Request -> formValues JSON 직렬화, LinkedHashMap으로 키 순서 고정
    private String serializeFormValues(EvaluationRulesSaveRequestDto req) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("itemList", req.getItemList());             // FormSnapshotDto.itemList 와 키 맞춤
        form.put("gradeRules", req.getGrades());              // FormSnapshotDto.gradeRules
        form.put("adjustments", req.getAdjustItems());        // FormSnapshotDto.adjustments
        form.put("rawScoreTable", req.getGradeItems());       // FormSnapshotDto.rawScoreTable
        form.put("kpiScoring", req.getKpiScoringConfig());    // FormSnapshotDto.kpiScoring
        try {
            return objectMapper.writeValueAsString(form);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("평가규칙 JSON 직렬화 실패", e);
        }
    }

    // 회사 생성 시 깔아둘 기본 규칙 JSON (formValues 섹션만 — 하드 컬럼은 엔티티 기본값 사용)
    public String buildDefaultFormValues() {
        Map<String, Object> form = new LinkedHashMap<>();

        // 평가 항목 — 자기평가 30, 상위자평가 70
        form.put("itemList", List.of(
                Map.of("id", "self",    "name", "자기평가",   "weight", 30, "locked", true, "enabled", true),
                Map.of("id", "manager", "name", "상위자평가", "weight", 70, "locked", true, "enabled", true)
        ));

        // 등급 체계 — S/A/B/C/D 표준 컷오프 + 강제배분 비율
        form.put("gradeRules", List.of(
                Map.of("id", "S", "label", "S", "minScore", 90, "ratio", 10, "color", "#7c3aed"),
                Map.of("id", "A", "label", "A", "minScore", 80, "ratio", 20, "color", "#2e9e6e"),
                Map.of("id", "B", "label", "B", "minScore", 70, "ratio", 40, "color", "#3b82f6"),
                Map.of("id", "C", "label", "C", "minScore", 60, "ratio", 20, "color", "#f59e0b"),
                Map.of("id", "D", "label", "D", "minScore",  0, "ratio", 10, "color", "#ef4444")
        ));

        // 가감점 — 지각 / 무단결근 (근태 이벤트 기반)
        form.put("adjustments", List.of(
                Map.of("id", "late",    "name", "지각",     "points", -1, "enabled", true),
                Map.of("id", "absence", "name", "무단결근", "points", -3, "enabled", true)
        ));

        // 등급 원점수 변환표 — 팀장 등급 → managerScore
        form.put("rawScoreTable", List.of(
                Map.of("gradeId", "S", "rawScore", 95),
                Map.of("gradeId", "A", "rawScore", 85),
                Map.of("gradeId", "B", "rawScore", 75),
                Map.of("gradeId", "C", "rawScore", 65),
                Map.of("gradeId", "D", "rawScore", 50)
        ));

        // KPI 점수 환산 규칙 — 업계 표준 (cap 120, 100점 만점 리스케일)
        form.put("kpiScoring", Map.of(
                "cap", 120,
                "scaleTo", 100,
                "maintainTolerance", 0,
                "underperformanceThreshold", 0,
                "underperformanceFactor", 1.0
        ));

        try {
            return objectMapper.writeValueAsString(form);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("기본 규칙 JSON 생성 실패", e);
        }
    }
}

package com.peoplecore.evaluation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.company.domain.Company;
import com.peoplecore.evaluation.domain.EvaluationRules;
import com.peoplecore.evaluation.dto.EvaluationRulesDto;
import com.peoplecore.evaluation.dto.EvaluationRulesSaveRequestDto;
import com.peoplecore.evaluation.repository.EvaluationRulesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 평가규칙 - 회사별 전사 공통 규칙 (Company 와 1:1, 시즌과 무관하게 자유 편집)
// 시즌 OPEN 시 buildMergedSnapshotJson 으로 하드컬럼+JSON 병합본 생성 → Season.formSnapshot 박제
@Service
@Transactional
public class EvaluationRulesService {

    // 시스템 고정 가감항목 id — 이름/점수/삭제 불가, 저장 시 DB 기준값 강제 유지
    private static final Set<String> LOCKED_ADJUST_IDS = Set.of("late", "absent");

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

        String formValuesJson = serializeFormValues(req, rules.getFormValues());
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
    //    - itemList: 기존 DB 의 locked 항목은 그대로 유지(덮어쓰기/삭제 차단), 나머지는 요청값으로 대체
    //    - 다른 섹션: 요청 null이면 기존 DB 값 유지 (한 섹션 누락이 전체 날리는 사고 방지)
    private String serializeFormValues(EvaluationRulesSaveRequestDto req, String existingJson) {
        Map<String, Object> existing = parseFormValuesSafely(existingJson);
        Map<String, Object> form = new LinkedHashMap<>();

        // itemList: DB 의 locked 항목 보호 + 요청의 non-locked 만 반영
        form.put("itemList", mergeItemList(req.getItemList(), existing));

        // 나머지 섹션: 요청 null이면 기존값 유지
        form.put("gradeRules",    req.getGrades() != null           ? req.getGrades()           : existing.get("gradeRules"));
        // adjustments: DB 의 locked 항목(지각/무단결근) 이름·점수 보호 + 요청의 non-locked 만 반영
        form.put("adjustments",   req.getAdjustItems() != null      ? mergeAdjustments(req.getAdjustItems(), existing) : existing.get("adjustments"));
        form.put("rawScoreTable", req.getGradeItems() != null       ? req.getGradeItems()       : existing.get("rawScoreTable"));
        form.put("kpiScoring",    req.getKpiScoringConfig() != null ? req.getKpiScoringConfig() : existing.get("kpiScoring"));

        try {
            return objectMapper.writeValueAsString(form);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("평가규칙 JSON 직렬화 실패", e);
        }
    }

    // itemList 병합 — 회사 생성 시 넣어둔 locked 항목은 덮어쓰기/삭제 불가
    private List<Map<String, Object>> mergeItemList(List<EvaluationRulesDto.EvalItem> reqItems,
                                                    Map<String, Object> existingForm) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> lockedIds = new HashSet<>();

        // DB의 itemList 를 EvalItem DTO 로 변환 (타입 안전)
        List<EvaluationRulesDto.EvalItem> existingItems = objectMapper.convertValue(
                existingForm.get("itemList"),
                new TypeReference<List<EvaluationRulesDto.EvalItem>>() {}
        );

        if (existingItems != null) {
            for (EvaluationRulesDto.EvalItem item : existingItems) {
                if (!Boolean.TRUE.equals(item.getLocked())) continue;
                Map<String, Object> copy = new LinkedHashMap<>();
                copy.put("id", item.getId());
                copy.put("jobTypeName", item.getName());
                copy.put("weight", item.getWeight());
                copy.put("locked", true);
                copy.put("enabled", item.getEnabled() != null ? item.getEnabled() : Boolean.TRUE);
                result.add(copy);
                if (item.getId() != null) lockedIds.add(item.getId());
            }
        }

        if (reqItems != null) {
            for (EvaluationRulesDto.EvalItem item : reqItems) {
                if (item == null || item.getId() == null) continue;
                if (lockedIds.contains(item.getId())) continue;  // locked ID 는 스킵 (덮어쓰기 차단)
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", item.getId());
                m.put("jobTypeName", item.getName());
                m.put("weight", item.getWeight());
                m.put("locked", false);
                m.put("enabled", item.getEnabled() != null ? item.getEnabled() : Boolean.TRUE);
                result.add(m);
            }
        }
        return result;
    }

    // adjustments 병합 — 지각/무단결근(LOCKED_ADJUST_IDS)은 DB 기준값 강제 유지
    //   · 이름/점수는 요청값 무시하고 DB 값으로 고정 (요청이 변조해도 서버에서 원복)
    //   · enabled 는 요청값이 있으면 반영 (사용자가 감점제 자체를 끌 순 있게)
    //   · 레거시 잔재(예: "absence") 등 LOCKED 이외 항목은 요청대로 통과
    private List<Map<String, Object>> mergeAdjustments(List<EvaluationRulesDto.AdjustItem> reqItems,
                                                       Map<String, Object> existingForm) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seenLocked = new HashSet<>();

        List<EvaluationRulesDto.AdjustItem> existingItems = objectMapper.convertValue(
                existingForm.get("adjustments"),
                new TypeReference<List<EvaluationRulesDto.AdjustItem>>() {}
        );
        Map<String, EvaluationRulesDto.AdjustItem> existingById = new LinkedHashMap<>();
        if (existingItems != null) {
            for (EvaluationRulesDto.AdjustItem it : existingItems) {
                if (it != null && it.getId() != null) existingById.put(it.getId(), it);
            }
        }

        // locked 항목 먼저 — DB 기준값 유지, 요청은 enabled 만 반영
        for (String lockedId : LOCKED_ADJUST_IDS) {
            EvaluationRulesDto.AdjustItem base = existingById.get(lockedId);
            if (base == null) continue;  // 시드 누락된 회사면 skip (createDefaultRules 재호출 필요)
            Boolean enabled = base.getEnabled() != null ? base.getEnabled() : Boolean.TRUE;
            if (reqItems != null) {
                for (EvaluationRulesDto.AdjustItem r : reqItems) {
                    if (r != null && lockedId.equals(r.getId()) && r.getEnabled() != null) {
                        enabled = r.getEnabled();
                        break;
                    }
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", lockedId);
            m.put("jobTypeName", base.getName());
            m.put("points", base.getPoints());
            m.put("enabled", enabled);
            m.put("locked", true);
            result.add(m);
            seenLocked.add(lockedId);
        }

        // non-locked 요청 항목 — locked id 로 위장한 것은 버림
        if (reqItems != null) {
            for (EvaluationRulesDto.AdjustItem r : reqItems) {
                if (r == null || r.getId() == null) continue;
                if (seenLocked.contains(r.getId())) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", r.getId());
                m.put("jobTypeName", r.getName());
                m.put("points", r.getPoints());
                m.put("enabled", r.getEnabled() != null ? r.getEnabled() : Boolean.TRUE);
                result.add(m);
            }
        }
        return result;
    }

    // 기존 form_values JSON 파싱 (null/파싱에러 방어 -> 빈 Map)
    private Map<String, Object> parseFormValuesSafely(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
            return parsed != null ? parsed : new LinkedHashMap<>();
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    // 회사 생성 시 깔아둘 기본 규칙 JSON (formValues 섹션만 — 하드 컬럼은 엔티티 기본값 사용)
    public String buildDefaultFormValues() {
        Map<String, Object> form = new LinkedHashMap<>();

        // 평가 항목 — 자기평가 30, 상위자평가 70
        form.put("itemList", List.of(
                Map.of("id", "self",    "jobTypeName", "자기평가",   "weight", 30, "locked", true, "enabled", true),
                Map.of("id", "manager", "jobTypeName", "상위자평가", "weight", 70, "locked", true, "enabled", true)
        ));

        // 등급 체계 — S/A/B/C/D 표준 강제배분 비율
        form.put("gradeRules", List.of(
                Map.of("id", "S", "label", "S", "ratio", 10, "color", "#7c3aed"),
                Map.of("id", "A", "label", "A", "ratio", 20, "color", "#2e9e6e"),
                Map.of("id", "B", "label", "B", "ratio", 40, "color", "#3b82f6"),
                Map.of("id", "C", "label", "C", "ratio", 20, "color", "#f59e0b"),
                Map.of("id", "D", "label", "D", "ratio", 10, "color", "#ef4444")
        ));

        // 가감점 — 지각 / 무단결근 (근태 이벤트 기반, FE LOCKED_ADJUST_IDS 와 id 일치 필수)
        form.put("adjustments", List.of(
                Map.of("id", "late",   "jobTypeName", "지각",     "points", -2, "enabled", true, "locked", true),
                Map.of("id", "absent", "jobTypeName", "무단결근", "points", -5, "enabled", true, "locked", true)
        ));

        // 등급 원점수 변환표 — 팀장 등급 → managerScore
        form.put("rawScoreTable", List.of(
                Map.of("gradeId", "S", "rawScore", 100),
                Map.of("gradeId", "A", "rawScore", 90),
                Map.of("gradeId", "B", "rawScore", 80),
                Map.of("gradeId", "C", "rawScore", 70),
                Map.of("gradeId", "D", "rawScore", 60)
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

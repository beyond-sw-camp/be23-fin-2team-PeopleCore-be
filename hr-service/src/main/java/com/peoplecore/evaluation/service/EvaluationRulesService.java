package com.peoplecore.evaluation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.evaluation.domain.EvalSeasonStatus;
import com.peoplecore.evaluation.domain.EvaluationRules;
import com.peoplecore.evaluation.domain.Season;
import com.peoplecore.evaluation.dto.EvaluationRulesDto;
import com.peoplecore.evaluation.dto.EvaluationRulesSaveRequestDto;
import com.peoplecore.evaluation.repository.EvaluationRulesRepository;
import com.peoplecore.evaluation.repository.SeasonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 평가규칙 - 항목/등급/가감점 커스텀 규칙
@Service
@Transactional
public class EvaluationRulesService {
    private final EvaluationRulesRepository rulesRepository;
    private final SeasonRepository seasonRepository;
    private final ObjectMapper objectMapper;


    public EvaluationRulesService(EvaluationRulesRepository rulesRepository, SeasonRepository seasonRepository, ObjectMapper objectMapper) {
        this.rulesRepository = rulesRepository;
        this.seasonRepository = seasonRepository;
        this.objectMapper = objectMapper;
    }
//     시즌규칙 조회 -row없을 시 null, dto변환 시 formSnapshot우선, 없으면 formValues fallback
    @Transactional(readOnly = true)
    public EvaluationRulesDto getBySeasonId(Long seasonId){
        EvaluationRules rules = rulesRepository.findBySeason_SeasonId(seasonId).orElse(null);
        return EvaluationRulesDto.from(rules,objectMapper);
    }



//    시즌규칙 저장/수정(upsert)// draft상태시즌만 편집허용-없으면 신규 생성, 있으면updateDraft로 갱신, 요청에 null필드 오면 기존값 유지
    public EvaluationRulesDto save(Long seasonId, EvaluationRulesSaveRequestDto requestDto){
//        시즌 존재 확인
        Season season = seasonRepository.findById(seasonId).orElseThrow(()->new IllegalArgumentException("시즌을 찾을 수 없습니다"));
//        Draft에서만 규칙 편집 허용
        if(season.getStatus() != EvalSeasonStatus.DRAFT){
            throw new IllegalStateException("Draft 상태 시즌만 규칙을 수정할 수 있습니다");
        }
//        동적 섹션 5종을 json문자열로 직렬화(from_values에 저장될 값)
        String formValuesJson = serializeFormValues(requestDto);
        EvaluationRulesDto.TaskGradeWeight tgw =requestDto.getTaskGradeWeight();

//        upsert분기
        EvaluationRules rules = rulesRepository.findBySeason_SeasonId(seasonId).orElse(null);
//         null 방어용 기본값 주입
        if(rules == null){
            rules = EvaluationRules.builder()
                    .season(season)
                    .taskWeightSang(tgw != null ? tgw.get상() : 3)
                    .taskWeightJung(tgw != null ? tgw.get중() : 2)
                    .taskWeightHa(tgw != null ? tgw.get하() :  1)
                    .useBiasAdjustment(requestDto.getUseBiasAdjustment() != null ? requestDto.getUseBiasAdjustment() : true) //편향 보정 사용여부
                    .biasWeight(requestDto.getBiasWeight() != null ? requestDto.getBiasWeight() : new BigDecimal("1.0")) //편향 보정 강도
                    .minTeamSize(requestDto.getMinTeamSize() != null ? requestDto.getMinTeamSize() : 5)
                    .formValues(formValuesJson)
                    .formVersion(0L)
                    .build();
            rules = rulesRepository.save(rules);
        }else{
            // 기존 수정 — null 오면 기존값 유지 (dirty checking 으로 UPDATE 자동 발행)
            rules.updateDraft(
                    tgw != null ? tgw.get상() : rules.getTaskWeightSang(),
                    tgw != null ? tgw.get중() : rules.getTaskWeightJung(),
                    tgw != null ? tgw.get하() : rules.getTaskWeightHa(),
                    requestDto.getUseBiasAdjustment() != null ? requestDto.getUseBiasAdjustment() : rules.getUseBiasAdjustment(),
                    requestDto.getBiasWeight() != null ? requestDto.getBiasWeight(): rules.getBiasWeight(),
                    requestDto.getMinTeamSize() != null ? requestDto.getMinTeamSize(): rules.getMinTeamSize(),
                    formValuesJson
            );
        }
        return EvaluationRulesDto.from(rules,objectMapper);
    }




//    시즌 open시 호출 formValues를 formSnapshot으로 동결(schedular)
    public  void freezeSnapshot(Long seasonId){
        EvaluationRules rules = rulesRepository.findBySeason_SeasonId(seasonId).orElseThrow(()->new IllegalStateException("규칙이 설정되지 않은 시즌입니다"));
        rules.freezeSnapshot();
    }


//    시즌 삭제 시 연관 규칙 row 제거(있을 수도 없을 수도) - SeasonService.deleteSeason에서 호출
    public void deleteBySeasonId(Long seasonId){
        rulesRepository.findBySeason_SeasonId(seasonId).ifPresent(rulesRepository::delete);
    }




    // 신규 시즌용 규칙 row 생성, 직전값있으면 해당규칙 복사, 없으면 기본값(buildDefaultFormValues)
    public EvaluationRules createInitialRules(Season newSeason) {
        UUID companyId = newSeason.getCompany().getCompanyId();

        // 같은 회사의 직전 시즌 규칙 조회 (신규 시즌 제외)
        List<EvaluationRules> recent =
                rulesRepository.findLatestByCompany(companyId, newSeason.getSeasonId());

        EvaluationRules.EvaluationRulesBuilder builder = EvaluationRules.builder()
                .season(newSeason)
                .formVersion(0L);

        if (!recent.isEmpty()) {
            // 직전 시즌 규칙 복사 — formSnapshot 우선, 없으면 formValues
            EvaluationRules last = recent.get(0);
            String sourceJson = last.getFormSnapshot() != null
                    ? last.getFormSnapshot()
                    : last.getFormValues();

            builder
                    .taskWeightSang(last.getTaskWeightSang())
                    .taskWeightJung(last.getTaskWeightJung())
                    .taskWeightHa(last.getTaskWeightHa())
                    .useBiasAdjustment(last.getUseBiasAdjustment())
                    .biasWeight(last.getBiasWeight())
                    .minTeamSize(last.getMinTeamSize())
                    .formValues(sourceJson);
        } else {
            // 첫 시즌 — 업계 표준 기본값, 하드 컬럼은 엔티티 @Builder.Default 로 자동 주입 (3/2/1/true/1.0/5)
            builder.formValues(buildDefaultFormValues());
        }
        return rulesRepository.save(builder.build());
    }
//    Request->form values Json직렬화, LinkedHashMap으로 키 순서 고정
    private String serializeFormValues(EvaluationRulesSaveRequestDto req) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("items", req.getItemList());               // DTO 필드: itemList
        form.put("grades", req.getGrades());                 // DTO 필드: grades
        form.put("adjustments", req.getAdjustItems());       // DTO 필드: adjustItems
        form.put("rawScoreTable", req.getGradeItems());      // DTO 필드: gradeItems (원점수 변환표)
        form.put("kpiScoring", req.getKpiScoringConfig());   // DTO 필드: kpiScoringConfig
        try {
            return objectMapper.writeValueAsString(form);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("평가규칙 JSON 직렬화 실패", e);
        }
    }




    // 시즌 생성 시 깔아둘 기본 규칙 json, 기본값 ->편집 시 덮힘
    public String buildDefaultFormValues() {
        Map<String, Object> form = new LinkedHashMap<>();

        // 평가 항목 — 자기평가 30, 상위자평가 70
        form.put("items", List.of(
                Map.of("id", "self",    "name", "자기평가",   "weight", 30),
                Map.of("id", "manager", "name", "상위자평가", "weight", 70)
        ));

        // 등급 체계 — S/A/B/C/D 표준 컷오프 + 강제배분 비율
        form.put("grades", List.of(
                Map.of("id", "S", "label", "S", "minScore", 90, "ratio", 10, "color", "#7c3aed"),
                Map.of("id", "A", "label", "A", "minScore", 80, "ratio", 20, "color", "#2e9e6e"),
                Map.of("id", "B", "label", "B", "minScore", 70, "ratio", 40, "color", "#3b82f6"),
                Map.of("id", "C", "label", "C", "minScore", 60, "ratio", 20, "color", "#f59e0b"),
                Map.of("id", "D", "label", "D", "minScore",  0, "ratio", 10, "color", "#ef4444")
        ));

        // 가감점 — 근태 / 징계 / 표창
        form.put("adjustments", List.of(
                Map.of("id", "attendance", "name", "근태 감점", "points", -2, "enabled", true),
                Map.of("id", "discipline", "name", "징계 감점", "points", -5, "enabled", true),
                Map.of("id", "award",      "name", "표창 가산", "points",  3, "enabled", true)
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



package com.peoplecore.evaluation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.department.domain.Department;
import com.peoplecore.department.repository.DepartmentRepository;
import com.peoplecore.evaluation.domain.EvalGrade;
import com.peoplecore.evaluation.domain.EvalGradeSortField;
import com.peoplecore.evaluation.domain.EvalSeasonStatus;
import com.peoplecore.evaluation.domain.EvaluationRules;
import com.peoplecore.evaluation.domain.Season;
import com.peoplecore.evaluation.dto.BiasAdjustResultDto;
import com.peoplecore.evaluation.dto.DraftListItemDto;
import com.peoplecore.evaluation.dto.FormSnapshotDto;
import com.peoplecore.evaluation.repository.EvalGradeRepository;
import com.peoplecore.evaluation.repository.EvaluationRulesRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;


// 등급 - 초안 자동 산정, 보정, 최종 확정, 결과 조회

@Service
@Transactional
public class EvalGradeService {

    private final EvalGradeRepository evalGradeRepository;
    private final EvaluationRulesRepository rulesRepository;
    private final DepartmentRepository departmentRepository;
    private final ObjectMapper objectMapper;

    public EvalGradeService(EvalGradeRepository evalGradeRepository,
                            EvaluationRulesRepository rulesRepository,
                            DepartmentRepository departmentRepository,
                            ObjectMapper objectMapper) {
        this.evalGradeRepository = evalGradeRepository;
        this.rulesRepository = rulesRepository;
        this.departmentRepository = departmentRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    // 1. 자동 산정 대상 목록 조회 //DB totalScore + autoGrade 그대로 반환 // - totalScore/autoGrade 가 null일시 미산정 -> 프론트 "-" 렌더
    //  부서/직급 시즌 오픈 시점 스냅샷 (조직개편 무관)
    public Page<DraftListItemDto> getDraftList(UUID companyId, Long seasonId, Long deptId, String keyword, EvalGradeSortField sortField, Pageable pageable) {
        return evalGradeRepository.searchDraftList(companyId, seasonId, deptId, keyword, sortField, pageable);
    }


    //    2.등급 자동 산정(수동/스케줄러 공통) //규칙 스냅샷 팟싱 ->가중치/등급체계추출
//    시즌 전사원 row순회하며 점수 집계 +autoGrade판정 +update //json돌면서 있는 항목 하나라도 미제출 시 스킵
    public void calculateAutoGrades(UUID companyId, Long seasonId) {

//        a)규칙 로드 + 소유권/상태 검증
        EvaluationRules rules = rulesRepository.findBySeason_SeasonId(seasonId)
                .orElseThrow(() -> new IllegalStateException("규칙 없음"));

        Season season = rules.getSeason();
//        회사 소유권 검증 (멀티테넌시)
        if (!season.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("접근 권한 없음");
        }
//        확정된 시즌은 재산정 불가
        if (season.getFinalizedAt() != null) {
            throw new IllegalStateException("확정된 시즌은 재산정 불가");
        }
//        OPEN 상태만 허용
        if (season.getStatus() != EvalSeasonStatus.OPEN) {
            throw new IllegalStateException("진행중 시즌만 산정 가능");
        }

//        스냅샷 파싱
        FormSnapshotDto snapshot;
        try {
            snapshot = objectMapper.readValue(rules.getFormSnapshot(), FormSnapshotDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("규칙 스냅샷 파싱 실패", e);
        }

        // 가중치 합 검증 - 비활성화된 잠금 항목(enabled=false)은 제외
        BigDecimal weightSum = BigDecimal.ZERO;
        for (FormSnapshotDto.Item item : snapshot.getItemList()) {
            if (isItemDisabled(item)) continue;
            weightSum = weightSum.add(item.getWeight());
        }
        if (weightSum.compareTo(new BigDecimal("100")) != 0) {
            throw new IllegalStateException("가중치의 합이 100이 되어야합니다");
        }

//        b. 시즌  전체 EvalGrade 로드
        List<EvalGrade> rows = evalGradeRepository.findBySeason_SeasonId(seasonId);

//        c. row마다 집계+ update
        for (EvalGrade row : rows) {
            Long empId = row.getEmp().getEmpId();

//            items순회로 가중평균 누적식으로 계산 (json목록 ex.자기./팀장 등)
            BigDecimal weightedSum = BigDecimal.ZERO;
            BigDecimal weightTotal = BigDecimal.ZERO;
            boolean missing = false;

            for (FormSnapshotDto.Item item : snapshot.getItemList()) {
                // 비활성화된 잠금 항목(enabled=false)은 집계 대상에서 제외
                if (isItemDisabled(item)) continue;

                BigDecimal score = aggregateScoreByItem(item, empId, seasonId, rules, snapshot);
                if (score == null) {
//                    해당 평가 미제출 -> 산정 스킵
                    missing = true;
                    break;
                }
                weightedSum = weightedSum.add(score.multiply(item.getWeight())); //점수 x가중치 누적합
                weightTotal = weightTotal.add(item.getWeight()); //가중치 합(프론트로 정합성 100)
            }
            if (missing || weightTotal.signum() == 0) {
//                재산정 시 미제출이면 이전 점수 전부 null 로 초기화 (유령값 방지)
                row.applyTotalScore(null, null, null);
                continue;
            }
//            가중평균
            BigDecimal weighted = weightedSum.divide(weightTotal, 2, RoundingMode.HALF_UP);


//            조정점수(근태 등)
            BigDecimal adjustment = aggregateAdustment(empId, seasonId, snapshot);

//            종합점수
            BigDecimal total = weighted.add(adjustment);

//            update(dirty checking) - 비율/가감/종합 분리 저장, autoGrade 는 3번 강제배분에서 부여
            row.applyTotalScore(weighted, adjustment, total);
        }
    }

    // 헬퍼

    // 잠금 항목(자기평가/상위자평가)의 체크박스 off 여부 판정
    //   - locked=true AND enabled=false 인 경우에만 비활성으로 간주
    //   - 일반 항목이나 enabled 미지정(null)/true 는 사용 중
    private boolean isItemDisabled(FormSnapshotDto.Item item) {
        return Boolean.TRUE.equals(item.getLocked())
                && Boolean.FALSE.equals(item.getEnabled());
    }

//    item별 점수 집계 -.id로 분기(ex.자기/팀장 점수 집계)
    private BigDecimal aggregateScoreByItem(FormSnapshotDto.Item item, Long empId, Long seasonId, EvaluationRules rules, FormSnapshotDto snapshotDto) {
//        TODO:self로직 생성후  achievementLevel 점수 매핑 + taskWeight 난이도 가중평균, manager 로직 생성후 gradeLabel 조회snapshot.rawScoreTable 로 점수 환산
        return null;//(미체출 시 null)
    }

    //    조정점수 집계 -enabled항목만 합산(조정점수 합산)
    private BigDecimal aggregateAdustment(Long empId, Long seasonId, FormSnapshotDto snapshot) {
        BigDecimal sum = BigDecimal.ZERO;
        for (FormSnapshotDto.Adjustment adj : snapshot.getAdjustments()) {
//            비활성 스킵
            if (!Boolean.TRUE.equals(adj.getEnabled())) {
                continue;
            }
//            TODO: 항목별 발생건후 조회 후 건당 point환산(부호로 가/감 자연 반영)
            long count = 0;
            if (count > 0) {
                sum = sum.add(adj.getPoints().multiply(BigDecimal.valueOf(count)));
            }
        }
        return sum;
    }


    //    3.z score편향 보정
    public void applyBiasAdjustment(UUID companyId, Long seasonId) {

//        a.규칙 로드+소유권
        EvaluationRules rules = rulesRepository.findBySeason_SeasonId(seasonId)
                .orElseThrow(() -> new IllegalStateException("규칙 없음"));

        Season season = rules.getSeason();
//        회사 소유권 검증 (멀티테넌시)
        if (!season.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("접근 권한 없음");
        }
//        확정된 시즌은 재산정 불가
        if (season.getFinalizedAt() != null) {
            throw new IllegalStateException("확정된 시즌은 재산정 불가");
        }
//        OPEN 상태만 허용
        if (season.getStatus() != EvalSeasonStatus.OPEN) {
            throw new IllegalStateException("진행중 시즌만 산정 가능");
        }

        // b.스냅샷 파싱
        FormSnapshotDto snapshot;
        try {
            snapshot = objectMapper.readValue(rules.getFormSnapshot(), FormSnapshotDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("규칙 스냅샷 파싱 실패", e);
        }

//        c.점수있는 row만 랭킹 대상, 없는 건 초기화
        List<EvalGrade> rows = evalGradeRepository.findBySeason_SeasonId(seasonId);
        List<EvalGrade> scored = new ArrayList<>();
        for (EvalGrade row : rows) {
            if (row.getTotalScore() != null) {
                scored.add(row);
            }
        }
        if (scored.isEmpty()) {
            return;
        }

//          d.팀별 그룹핑- 팀별 평균·표준편차 계산 -> Z-score 편향보정을 위함
        Map<Long, List<EvalGrade>> byTeam = new HashMap<>();
        for (EvalGrade row : scored) {
            Long deptId = row.getDeptIdSnapshot(); // 이 직원의소속 팀 부서Id
            List<EvalGrade> list = byTeam.get(deptId); // 팀의 리스트가 없을 경우 새로 만들어서 map
            if (list == null) {
                list = new ArrayList<>();
                byTeam.put(deptId, list);
            }
            list.add(row);
        }

        // e. 전사 평균/표편 (Z-score 리스케일 기준)
        BigDecimal companyAvg = calcAvg(scored);
        BigDecimal companyStd = calcStdDev(scored, companyAvg);

        // f. 편향보정 off -> totalScore 그대로 복사 (감사/일관성)
        if (!Boolean.TRUE.equals(rules.getUseBiasAdjustment())) {
            for (EvalGrade row : scored) {
                int ts = byTeam.get(row.getDeptIdSnapshot()).size();
                row.applyBiasAdjustment(row.getTotalScore(), null, null, companyAvg, companyStd, ts);
            }
            return;
        }

        // g. 팀별 Z-score 보정
        //    - 이상 팀(소규모/전원 동점)은 원점수 유지
        //    - teamStdDev/teamSize 스냅샷이 EvalGrade에 기록되므로
        //      화면 표시용 이상 팀 수집은 별도 조회 API(getBiasAdjustAnomalies)가 담당
        int minTeamSize = snapshot.getMinTeamSize() != null ? snapshot.getMinTeamSize() : 5;

        // 팀별 처리
        for (Map.Entry<Long, List<EvalGrade>> entry : byTeam.entrySet()) {
            List<EvalGrade> members = entry.getValue();   // 부서의 사원들
            int teamSize = members.size();

            // 팀 내부 통계
            BigDecimal teamAvg = calcAvg(members);
            BigDecimal teamStd = calcStdDev(members, teamAvg);

            // 소규모 팀 여부 체크 (팀 단위)
            boolean undersized = teamSize < minTeamSize;

            // 팀원 한 명씩 처리
            for (EvalGrade row : members) {
                BigDecimal biased;

                if (undersized || teamStd.signum() == 0) {
                    // 보정 스킵: 소규모 팀 OR 전원 동점 → 원점수 유지
                    biased = row.getTotalScore();
                } else {
                    // 정상 보정: Z = (x - μ_team) / σ_team
                    BigDecimal z = row.getTotalScore()
                            .subtract(teamAvg)
                            .divide(teamStd, 6, RoundingMode.HALF_UP);

                    // 역표준화: x = μ_co + Z × σ_co → 전사 분포로 리스케일
                    biased = companyAvg.add(z.multiply(companyStd))
                            .setScale(2, RoundingMode.HALF_UP);
                }

                // 보정 결과 + 통계 스냅샷을 EvalGrade에 기록
                row.applyBiasAdjustment(biased, teamAvg, teamStd, companyAvg, companyStd, teamSize);
            }
        }
    }





//    4.강제배분 등급적용 - 보정전// 규칙 스냅샷+소유권 상태//점수 있는 row만 내림차순// ratio대로 위에서 배정-> 동일 점수=비율>가감
//    없는 점수row= autoGrade/순위 null세팅

    public void applyDistribution(UUID companyId, Long seasonId) {

//        규칙 로드
        EvaluationRules rules = rulesRepository.findBySeason_SeasonId(seasonId).orElseThrow(() -> new IllegalStateException("규칙 없음"));
//        소유권/상태검증
        Season season = rules.getSeason();
        if (!season.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다");
        }
        if (season.getFinalizedAt() != null) {
            throw new IllegalStateException("마감된 시즌을 재분배 불가합니다"); // 재오픈-확장 방어용
        }
        if (season.getStatus() != EvalSeasonStatus.OPEN) {
            throw new IllegalStateException("진행중인 시즌만 재분배가 가능합니다");
        }

//        스냅샷 파싱
        FormSnapshotDto snapshot;
        try {
            snapshot = objectMapper.readValue(rules.getFormSnapshot(), FormSnapshotDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("규칙 스냅샷 파싱 실패", e);
        }

//        보정점수 있는 row만 랭킹 대상, 없는 건 초기화
        List<EvalGrade> rows = evalGradeRepository.findBySeason_SeasonId(seasonId);
        List<EvalGrade> ranked = new ArrayList<>();
        for (EvalGrade row : rows) {
            if (row.getBiasAdjustedScore() != null) {
                ranked.add(row);
            } else {
                row.applyDistribution(null, null);
            }
        }

//        biasAdjustedScore 내림차순 정렬, 동점 시 weightedScore 높은 사원 우선
//        (편향보정 결과로 팀장 편향 제거된 점수 기준 → 등급 배분의 공정성 확보)
        ranked.sort(new Comparator<EvalGrade>() {
            @Override
            public int compare(EvalGrade a, EvalGrade b) {
//                1순위: 편향보정 점수 내림차순
                int c = b.getBiasAdjustedScore().compareTo(a.getBiasAdjustedScore());
                if (c != 0) return c;
//                2순위: 가중치 점수 내림차순 (동점자 타이브레이크)
                return b.getWeightedScore().compareTo(a.getWeightedScore());
            }
        });

//       ratio대로 위에서부터 등급 배정(마지막 등급은 잔여몰기)
        int total = ranked.size();
        List<FormSnapshotDto.GradeRule> gradeRules = snapshot.getGradeRules(); //등급규칙목록
        int idx = 0;
//      등급순회
        for (int gi = 0; gi < gradeRules.size(); gi++) {
            FormSnapshotDto.GradeRule g = gradeRules.get(gi);

            int quota; //등급에 배정할 인원수
            if (gi == gradeRules.size() - 1) { //반올림
                quota = total - idx; //마지막 등급에 잔여인원 모두 배치(인원수 정합성)
            } else {//반올림
                quota = (int) Math.round(total * g.getRatio().doubleValue() / 100.0);
            }
            // 현재 idx 위치부터 quota 명에게 이 등급(g.getLabel())과 순위(idx+1) 부여
            for (int i = 0; i < quota && idx < total; i++, idx++) {
                ranked.get(idx).applyDistribution(g.getLabel(), idx + 1);
            }

        }

    }

//  3.zscore
//    산술평균: 점수 합/인원수 -팀평균/전사평균
    private BigDecimal calcAvg(List<EvalGrade>list){
        if(list.isEmpty()){
            return BigDecimal.ZERO;
        }
//        모든 totalScore합산
        BigDecimal sum = BigDecimal.ZERO;
        for(EvalGrade e :list){
            sum = sum.add(e.getTotalScore());
        }
//        평균 = 합/인원수(소수 6자리 반올림)
        return sum.divide(BigDecimal.valueOf(list.size()),6,RoundingMode.HALF_UP);
    }
//    모표준편차:√( Σ(x - μ)² / n )
//    전원 동점 시 감지
    private BigDecimal calcStdDev(List<EvalGrade>list,BigDecimal avg){
        if(list.isEmpty()){
            return BigDecimal.ZERO;
        }
//        각 점수의 편차 제곱(x - μ)² 누적
        BigDecimal variance =BigDecimal.ZERO;
        for(EvalGrade e : list){
            BigDecimal diff = e.getTotalScore().subtract(avg);//(x - μ)
            variance = variance.add(diff.multiply(diff)); // 제곱 후 누적

//            분산 = Σ(x - μ)² ÷ n
            variance = variance.divide(BigDecimal.valueOf(list.size()),6,RoundingMode.HALF_UP);

//            표준편차 =√분산 (정밀도 10자리)
            return variance.sqrt(new MathContext(10));
        }
    }



//    4. 이상보정조회 부서id집함 -> TeamAnomalyDto리스트 변환, batch조회
    private List<BiasAdjustResultDto.TeamAnomalyDto>buildTeamInfos(Collection<Long>deptIds){
//       빈 입력 시 빈 리스트 반환)
        if(deptIds.isEmpty()){
            return Collections.emptyList();
        }
//        전달받은 id전부를 한번의 쿼리로 조회
        List<Department>depts =departmentRepository.findAllById(deptIds);

//        조회 결과 map(id ->department)반환
        Map<Long, Department>deptMap = new HashMap<>();
        for(Department d: depts){
            deptMap.put(d.getDeptId(),d);
        }
//        입력순서대로 순회하며 dto생성
        List<BiasAdjustResultDto.TeamAnomalyDto>result = new ArrayList<>();
        for(Long id : deptIds){
            Department d =deptMap.get(id);

            if(d!=null){
//                조회 성공 시 정상 반환
                result.add(BiasAdjustResultDto.TeamAnomalyDto.from(d));
            }else{
//                조회 실패 시 ->폴백 dto
                result.add(BiasAdjustResultDto.TeamAnomalyDto.ofMissing(id));
            }
        }
        return result;
    }


}

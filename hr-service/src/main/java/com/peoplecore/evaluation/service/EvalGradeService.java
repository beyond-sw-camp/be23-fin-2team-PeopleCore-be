package com.peoplecore.evaluation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.department.domain.Department;
import com.peoplecore.department.repository.DepartmentRepository;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.evaluation.domain.DiffStatus;
import com.peoplecore.evaluation.domain.EvalGrade;
import com.peoplecore.evaluation.domain.EvalGradeSortField;
import com.peoplecore.evaluation.domain.EvalSeasonStatus;
import com.peoplecore.evaluation.domain.Season;
import com.peoplecore.evaluation.domain.Calibration;
import com.peoplecore.evaluation.dto.*;
import com.peoplecore.evaluation.domain.Stage;
import com.peoplecore.evaluation.domain.StageStatus;
import com.peoplecore.evaluation.domain.StageType;
import com.peoplecore.evaluation.repository.CalibrationRepository;
import com.peoplecore.evaluation.repository.EvalGradeRepository;
import com.peoplecore.evaluation.repository.SeasonRepository;
import com.peoplecore.evaluation.repository.StageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;


// 등급 - 초안 자동 산정, 보정, 최종 확정, 결과 조회

@Service
@Transactional
public class EvalGradeService {

    private final EvalGradeRepository evalGradeRepository;
    private final SeasonRepository seasonRepository;
    private final DepartmentRepository departmentRepository;
    private final CalibrationRepository calibrationRepository;
    private final StageRepository stageRepository;
    private final ObjectMapper objectMapper;
    private final EmployeeRepository employeeRepository;

    public EvalGradeService(EvalGradeRepository evalGradeRepository,
                            SeasonRepository seasonRepository,
                            DepartmentRepository departmentRepository,
                            CalibrationRepository calibrationRepository,
                            StageRepository stageRepository,
                            ObjectMapper objectMapper, EmployeeRepository employeeRepository) {
        this.evalGradeRepository = evalGradeRepository;
        this.seasonRepository = seasonRepository;
        this.departmentRepository = departmentRepository;
        this.calibrationRepository = calibrationRepository;
        this.stageRepository = stageRepository;
        this.objectMapper = objectMapper;
        this.employeeRepository = employeeRepository;
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

//        a)시즌 로드 + 소유권/상태 검증 (스냅샷은 Season.formSnapshot 에 박제되어 있음)
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new IllegalStateException("시즌 없음"));

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
//        단계 검증 (4단계 또는 5단계가 진행중이어야 수동 산정 가능)
        requireGradingStageOpen(seasonId);

//        스냅샷 파싱 (시즌 OPEN 시 박제된 병합 JSON)
        FormSnapshotDto snapshot;
        try {
            snapshot = objectMapper.readValue(season.getFormSnapshot(), FormSnapshotDto.class);
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

//            items순회로 가중평균 누적식으로 계산
//            - 자기평가(self), 상위자평가(manager) 점수는 별도 변수에 따로 보관
//              → 이후 applyBiasAdjustment에서 상위자점수에만 Z-score 보정을 적용하기 위함
            BigDecimal weightedSum = BigDecimal.ZERO;
            BigDecimal weightTotal = BigDecimal.ZERO;
            BigDecimal selfScore = null;     // 자기평가 원점수 (편향보정 제외)
            BigDecimal managerScore = null;  // 상위자평가 원점수 (편향보정 대상)
            boolean missing = false;

            for (FormSnapshotDto.Item item : snapshot.getItemList()) {
                // 비활성화된 잠금 항목(enabled=false)은 집계 대상에서 제외
                if (isItemDisabled(item)) continue;

                BigDecimal score = aggregateScoreByItem(item, empId, seasonId, snapshot);
                if (score == null) {
//                    해당 평가 미제출 -> 산정 스킵
                    missing = true;
                    break;
                }

                // 시스템 고정 항목 ID로 원점수 분리 저장
                if ("self".equals(item.getId())) {
                    selfScore = score;
                } else if ("manager".equals(item.getId())) {
                    managerScore = score;
                }

                weightedSum = weightedSum.add(score.multiply(item.getWeight())); //점수 x가중치 누적합
                weightTotal = weightTotal.add(item.getWeight()); //가중치 합(프론트로 정합성 100)
            }
            if (missing || weightTotal.signum() == 0) {
//                재산정 시 미제출이면 이전 점수 전부 null 로 초기화 (유령값 방지)
                row.applyTotalScore(null, null, null, null, null);
                continue;
            }
//            가중평균
            BigDecimal weighted = weightedSum.divide(weightTotal, 2, RoundingMode.HALF_UP);


//            조정점수(근태 등)
            BigDecimal adjustment = aggregateAdustment(empId, seasonId, snapshot);

//            종합점수
            BigDecimal total = weighted.add(adjustment);

//            update(dirty checking) - self/manager 원점수 + 비율/가감/종합 저장
            row.applyTotalScore(selfScore, managerScore, weighted, adjustment, total);
        }
    }

    // 헬퍼

    // GRADING 또는 FINALIZATION 단계가 IN_PROGRESS인지 검증
    private void requireGradingStageOpen(Long seasonId) {
        List<Stage> stages = stageRepository.findBySeason_SeasonId(seasonId);
        boolean open = false;
        for (Stage stage : stages) {
            if ((stage.getType() == StageType.GRADING || stage.getType() == StageType.FINALIZATION)
                    && stage.getStatus() == StageStatus.IN_PROGRESS) {
                open = true;
                break;
            }
        }
        if (!open) {
            throw new IllegalStateException("등급 산정 또는 결과확정 단계가 진행중이 아닙니다");
        }
    }

    // 잠금 항목(자기평가/상위자평가)의 체크박스 off 여부 판정
    //   - locked=true AND enabled=false 인 경우에만 비활성으로 간주
    //   - 일반 항목이나 enabled 미지정(null)/true 는 사용 중
    private boolean isItemDisabled(FormSnapshotDto.Item item) {
        return Boolean.TRUE.equals(item.getLocked())
                && Boolean.FALSE.equals(item.getEnabled());
    }

    //    item별 점수 집계 -.id로 분기(ex.자기/팀장 점수 집계)
    private BigDecimal aggregateScoreByItem(FormSnapshotDto.Item item, Long empId, Long seasonId, FormSnapshotDto snapshotDto) {
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

//        a.시즌 로드+소유권
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new IllegalStateException("시즌 없음"));

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
//        단계 검증
        requireGradingStageOpen(seasonId);

        // b.스냅샷 파싱 (시즌 OPEN 시 박제된 병합 JSON)
        FormSnapshotDto snapshot;
        try {
            snapshot = objectMapper.readValue(season.getFormSnapshot(), FormSnapshotDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("규칙 스냅샷 파싱 실패", e);
        }

//        c. 상위자점수 있는 row만 보정 대상 (상위자평가 기준 Z-score)
        List<EvalGrade> rows = evalGradeRepository.findBySeason_SeasonId(seasonId);
        List<EvalGrade> scored = new ArrayList<>();
        for (EvalGrade row : rows) {
            if (row.getManagerScore() != null) {
                scored.add(row);
            }
        }
        if (scored.isEmpty()) {
            return;
        }

//        d. 자기/상위자 가중치 추출 (스냅샷 기반)
//           - 상위자평가 보정 후 total 재계산 시 사용
//           - 비활성 항목은 제외
        BigDecimal selfWeight = findActiveWeight(snapshot, "self");
        BigDecimal managerWeight = findActiveWeight(snapshot, "manager");
        BigDecimal weightSum = selfWeight.add(managerWeight);

//          e.팀별 그룹핑 (deptIdSnapshot 기준)
        Map<Long, List<EvalGrade>> byTeam = new HashMap<>();
        for (EvalGrade row : scored) {
            Long deptId = row.getDeptIdSnapshot();
            List<EvalGrade> list = byTeam.get(deptId);
            if (list == null) {
                list = new ArrayList<>();
                byTeam.put(deptId, list);
            }
            list.add(row);
        }

        // f. 전사 "상위자점수" 평균/표편 (Z-score 리스케일 기준)
        BigDecimal companyAvg = calcMgrAvg(scored);
        BigDecimal companyStd = calcMgrStdDev(scored, companyAvg);

        // g. 편향보정 off -> managerScoreAdjusted = managerScore 그대로 (감사/일관성)
        if (!Boolean.TRUE.equals(snapshot.getUseBiasAdjustment())) {
            for (EvalGrade row : scored) {
                int ts = byTeam.get(row.getDeptIdSnapshot()).size();
                BigDecimal newTotal = recalcTotal(row, row.getManagerScore(), selfWeight, managerWeight, weightSum);
                row.applyBiasAdjustment(row.getManagerScore(), newTotal, null, null, companyAvg, companyStd, ts);
            }
            return;
        }

        // h. 팀별 Z-score 보정 (상위자점수만)
        //    - 이상 팀(소규모/전원 동점)은 상위자점수 원점수 유지
        //    - 보정 후 totalScore 재계산: (self × selfW + adjustedMgr × mgrW) / weightSum + adjustment
        int minTeamSize = snapshot.getMinTeamSize() != null ? snapshot.getMinTeamSize() : 5;

        for (Map.Entry<Long, List<EvalGrade>> entry : byTeam.entrySet()) {
            List<EvalGrade> members = entry.getValue();
            int teamSize = members.size();

            // 팀 내부 상위자점수 통계
            BigDecimal teamAvg = calcMgrAvg(members);
            BigDecimal teamStd = calcMgrStdDev(members, teamAvg);

            boolean undersized = teamSize < minTeamSize;

            for (EvalGrade row : members) {
                BigDecimal adjustedMgr;

                if (undersized || teamStd.signum() == 0) {
                    // 보정 스킵 → 상위자점수 그대로
                    adjustedMgr = row.getManagerScore();
                } else {
                    // Z = (상위자점수 - 팀평균) / 팀표편
                    BigDecimal z = row.getManagerScore()
                            .subtract(teamAvg)
                            .divide(teamStd, 6, RoundingMode.HALF_UP);

                    // 역표준화 → 전사 분포로 리스케일
                    adjustedMgr = companyAvg.add(z.multiply(companyStd))
                            .setScale(2, RoundingMode.HALF_UP);
                }

                // 보정된 상위자점수로 최종 totalScore 재계산
                BigDecimal newTotal = recalcTotal(row, adjustedMgr, selfWeight, managerWeight, weightSum);

                // 결과 저장: managerScoreAdjusted + biasAdjustedScore(재계산된 total) + 통계 스냅샷
                row.applyBiasAdjustment(adjustedMgr, newTotal, teamAvg, teamStd, companyAvg, companyStd, teamSize);
            }
        }
    }


    //    팀장 편향 보정(Z-score) 팀별 효과 요약 (자동 산정 화면 차트용)
    //    - 부서별로 managerScore(보정 전) / managerScoreAdjusted(보정 후) 평균 집계
    //    - minTeamSize 는 시즌 OPEN 당시 박제된 formSnapshot 기준 (실제 보정에 적용된 값)
    @Transactional(readOnly = true)
    public TeamBiasResponseDto getTeamBiasSummary(UUID companyId, Long seasonId) {
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new IllegalArgumentException("시즌을 찾을 수 없습니다"));

        // 회사 소유권 검증
        if (!season.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("접근 권한이 없는 시즌입니다");
        }

        // 시즌 스냅샷에서 minTeamSize 추출 (없으면 기본 5)
        int minTeamSize = 5;
        if (season.getFormSnapshot() != null) {
            try {
                FormSnapshotDto snapshot = objectMapper.readValue(season.getFormSnapshot(), FormSnapshotDto.class);
                if (snapshot.getMinTeamSize() != null) {
                    minTeamSize = snapshot.getMinTeamSize();
                }
            } catch (JsonProcessingException e) {
                // 파싱 실패 시 기본값 유지
            }
        }

        List<TeamBiasResponseDto.Team> teams = evalGradeRepository.findTeamBiasSummary(seasonId);
        return new TeamBiasResponseDto(minTeamSize, teams);
    }


    //    보정된 상위자점수 + 기존 자기점수/조정점수로 최종 점수 재계산
//    = (self × selfWeight + adjustedMgr × mgrWeight) / weightSum + adjustment
    private BigDecimal recalcTotal(EvalGrade row, BigDecimal adjustedMgr,
                                   BigDecimal selfWeight, BigDecimal mgrWeight, BigDecimal weightSum) {
        if (weightSum.signum() == 0) return row.getTotalScore();   // 가중치 0이면 기존 total 유지

        BigDecimal selfPart = row.getSelfScore() != null
                ? row.getSelfScore().multiply(selfWeight)
                : BigDecimal.ZERO;
        BigDecimal mgrPart = adjustedMgr.multiply(mgrWeight);

        BigDecimal weighted = selfPart.add(mgrPart).divide(weightSum, 2, RoundingMode.HALF_UP);
        BigDecimal adjustment = row.getAdjustmentScore() != null
                ? row.getAdjustmentScore()
                : BigDecimal.ZERO;
        return weighted.add(adjustment).setScale(2, RoundingMode.HALF_UP);
    }

    //    스냅샷에서 특정 id의 항목 가중치 조회 (비활성이면 0 반환)
    private BigDecimal findActiveWeight(FormSnapshotDto snapshot, String id) {
        for (FormSnapshotDto.Item item : snapshot.getItemList()) {
            if (!id.equals(item.getId())) continue;
            if (isItemDisabled(item)) return BigDecimal.ZERO;
            return item.getWeight() != null ? item.getWeight() : BigDecimal.ZERO;
        }
        return BigDecimal.ZERO;
    }


//    5.강제배분 등급적용 - 보정전// 규칙 스냅샷+소유권 상태//점수 있는 row만 내림차순// ratio대로 위에서 배정-> 동일 점수=비율>가감
//    없는 점수row= autoGrade/순위 null세팅
//    재실행 시: cohort(참여자 수) 변화 없으면 no-op, 변화 있고 보정 이력 있으면 confirm 필요
//              confirm=true 면 보정 전부 리셋 후 재배분

    public DistributionApplyResultDto applyDistribution(UUID companyId, Long seasonId, boolean confirm) {

//        시즌 로드
        Season season = seasonRepository.findById(seasonId).orElseThrow(() -> new IllegalStateException("시즌 없음"));
//        소유권/상태검증
        if (!season.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다");
        }
        if (season.getFinalizedAt() != null) {
            throw new IllegalStateException("마감된 시즌을 재분배 불가합니다"); // 재오픈-확장 방어용
        }
        if (season.getStatus() != EvalSeasonStatus.OPEN) {
            throw new IllegalStateException("진행중인 시즌만 재분배가 가능합니다");
        }
//        단계 검증
        requireGradingStageOpen(seasonId);

//        cohort 변화 판정: 현재 랭킹 대상 수 vs 이전 배분된 인원 수
        long currentRankedCount = evalGradeRepository.countBySeason_SeasonIdAndBiasAdjustedScoreNotNull(seasonId);
        long previousGradedCount = evalGradeRepository.countBySeason_SeasonIdAndAutoGradeNotNull(seasonId);
        boolean cohortChanged = (currentRankedCount != previousGradedCount);

//        cohort 변화 없음 -> no-op (보정 작업 보호)
//        이전 배분 결과와 동일하게 나올 것이므로 autoGrade/보정 둘 다 손대지 않음
        if (!cohortChanged && previousGradedCount > 0) {
            return DistributionApplyResultDto.builder()
                    .success(false)
                    .noChange(true)
                    .build();
        }

//        보정 이력 개수 조회 (cohort 바뀐 경우만 의미 있음)
        long calibCount = calibrationRepository.countByGrade_Season_SeasonId(seasonId);

//        보정 이력 존재 + confirm=false -> 확인 필요 (DB 변경 없이 반환)
        if (calibCount > 0 && !confirm) {
            return DistributionApplyResultDto.builder()
                    .success(false)
                    .requiresConfirm(true)
                    .pendingResetCount((int) calibCount)
                    .build();
        }

//        보정 리셋 (이력 삭제 + isCalibrated 플래그는 아래 row 순회에서 리셋)
        int resetCount = 0;
        if (calibCount > 0) {
            calibrationRepository.deleteAllByGrade_Season_SeasonId(seasonId);
            resetCount = (int) calibCount;
        }

//        스냅샷 파싱 (시즌 OPEN 시 박제된 병합 JSON)
        FormSnapshotDto snapshot;
        try {
            snapshot = objectMapper.readValue(season.getFormSnapshot(), FormSnapshotDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("규칙 스냅샷 파싱 실패", e);
        }

//        보정점수 있는 row만 랭킹 대상, 없는 건 초기화
//        전 row 순회하면서 isCalibrated 도 함께 리셋 (이미 로드했으므로 추가 쿼리 불필요)
        List<EvalGrade> rows = evalGradeRepository.findBySeason_SeasonId(seasonId);
        List<EvalGrade> ranked = new ArrayList<>();
        for (EvalGrade row : rows) {
            row.resetCalibration();
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

        return DistributionApplyResultDto.builder()
                .success(true)
                .distributedCount(total)
                .resetCount(resetCount)
                .build();
    }


//  Z-score용 통계 헬퍼 - 상위자점수(managerScore) 기준
//  (편향보정은 상위자평가에만 적용되므로 managerScore만 대상)

    //    상위자점수 산술평균: Σ managerScore / n
    private BigDecimal calcMgrAvg(List<EvalGrade> list) {
        if (list.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (EvalGrade e : list) {
            BigDecimal v = e.getManagerScore();
            if (v != null) sum = sum.add(v);
        }
        return sum.divide(BigDecimal.valueOf(list.size()), 6, RoundingMode.HALF_UP);
    }

    //    상위자점수 모표준편차: √( Σ(x - μ)² / n )
//    - 편차 제곱 누적 -> 분산 -> √
//    - 전원 동점이면 0 반환 (signum() == 0 으로 감지)
    private BigDecimal calcMgrStdDev(List<EvalGrade> list, BigDecimal avg) {
        if (list.isEmpty()) return BigDecimal.ZERO;

        BigDecimal variance = BigDecimal.ZERO;
        for (EvalGrade e : list) {
            BigDecimal v = e.getManagerScore();
            if (v == null) continue;
            BigDecimal diff = v.subtract(avg);                  // (x - μ)
            variance = variance.add(diff.multiply(diff));       // 제곱 누적
        }
        // 분산 = Σ(x - μ)² / n
        variance = variance.divide(BigDecimal.valueOf(list.size()), 6, RoundingMode.HALF_UP);
        // 표준편차 = √분산
        return variance.sqrt(new MathContext(10));
    }


    //    4. 편향보정 이상 팀 조회 (프론트 GradeCalibration 화면 진입 시 호출)
//       DB에 저장된 teamStdDev/teamSize 스냅샷을 읽어 이상 팀 복원 ->  단순 조회
    @Transactional(readOnly = true)
    public BiasAdjustResultDto getBiasAdjustAnomalies(UUID companyId, Long seasonId) {

//        a. 시즌 로드 + 소유권 검증
        Season season = seasonRepository.findById(seasonId).orElseThrow(() -> new IllegalStateException("시즌 없음"));
        if (!season.getCompany().getCompanyId().equals(companyId)) {    // 회사 소유권 검증
            throw new IllegalArgumentException("접근 권한 없음");
        }

//        b. 소규모 팀 기준(minTeamSize) 로드 - 보정 당시 스냅샷과 같은 기준 사용
        int minTeamSize = 5;                                            // 기본값
        if (season.getFormSnapshot() != null) {
            try {
                FormSnapshotDto snap = objectMapper.readValue(season.getFormSnapshot(), FormSnapshotDto.class);           // JSON 파싱
                if (snap.getMinTeamSize() != null) {
                    minTeamSize = snap.getMinTeamSize();                // 스냅샷 값으로 덮어씀
                }
            } catch (JsonProcessingException ignored) {
//                파싱 실패해도 기본값(5)으로 조회 계속 진행
            }
        }

//        c. 해당 시즌의 모든 EvalGrade 레코드 조회
        List<EvalGrade> rows = evalGradeRepository.findBySeason_SeasonId(seasonId);

//        d. 이상 팀 수집용 변수
        Set<Long> zeroStdDevTeams = new LinkedHashSet<>();  // 전원 동점 팀 ID (중복 방지 + 순서 유지)
        Set<Long> undersizedTeams = new LinkedHashSet<>();  // 소규모 팀 ID
        Set<Long> seenDepts = new HashSet<>();              // 팀당 한 번만 검사
        int processedCount = 0;                             // 보정 처리된 인원 수 (0이면 미실행)

//        e. 레코드 순회하며 이상 팀 판정
        for (EvalGrade row : rows) {
//            biasAdjustedScore 가 null 아니면 보정 실행된 인원 -> 카운트
            if (row.getBiasAdjustedScore() != null) {
                processedCount++;
            }

            Long deptId = row.getDeptIdSnapshot();                      // 당시 소속 팀 ID (박제값)

//            deptId 없거나 이미 검사한 팀이면 스킵 (팀당 한 번만 판정)
            if (deptId == null || !seenDepts.add(deptId)) {
                continue;
            }

//            소규모 팀 판정: 팀원 수 < 기준치
            if (row.getTeamSize() != null && row.getTeamSize() < minTeamSize) {
                undersizedTeams.add(deptId);
            }

//            전원 동점 팀 판정: 상위자점수 팀 표편 == 0
//              - null 체크: 편향보정 OFF 로 실행됐으면 null
//              - signum() == 0: BigDecimal scale 영향 없이 "값이 0" 안전 판정
            if (row.getTeamStdDev() != null && row.getTeamStdDev().signum() == 0) {
                zeroStdDevTeams.add(deptId);
            }
        }

//        f. DTO 조립 후 반환 (화면 배너 렌더링용)
        return BiasAdjustResultDto.builder()
                .seasonId(seasonId)                                     // 시즌 ID
                .processedCount(processedCount)                         // 처리 인원수
                .zeroStdDevTeams(buildTeamInfos(zeroStdDevTeams))       // 동점 팀 목록 (DTO)
                .undersizedTeams(buildTeamInfos(undersizedTeams))       // 소규모 팀 목록 (DTO)
                .build();
    }


    //    5. 이상보정조회 부서id집합 -> TeamAnomalyDto리스트 변환, batch조회
    private List<BiasAdjustResultDto.TeamAnomalyDto> buildTeamInfos(Collection<Long> deptIds) {
//       빈 입력 시 빈 리스트 반환)
        if (deptIds.isEmpty()) {
            return Collections.emptyList();
        }
//        전달받은 id전부를 한번의 쿼리로 조회
        List<Department> depts = departmentRepository.findAllById(deptIds);

//        조회 결과 map(id ->department)반환
        Map<Long, Department> deptMap = new HashMap<>();
        for (Department d : depts) {
            deptMap.put(d.getDeptId(), d);
        }
//        입력순서대로 순회하며 dto생성
        List<BiasAdjustResultDto.TeamAnomalyDto> result = new ArrayList<>();
        for (Long id : deptIds) {
            Department d = deptMap.get(id);

            if (d != null) {
//                조회 성공 시 정상 반환
                result.add(BiasAdjustResultDto.TeamAnomalyDto.from(d));
            } else {
//                조회 실패 시 ->폴백 dto
                result.add(BiasAdjustResultDto.TeamAnomalyDto.ofMissing(id));
            }
        }
        return result;
    }


    //    6. 실제 vs 목표 분포 + 보정 건수 조회 (GradeCalibration 화면 초기 진입)
//       - 실제: DB finalGrade 집계 (보정 반영) + 실시간 변동은 프론트
    @Transactional(readOnly = true)
    public DistributionDiffDto getDistributionDiff(UUID companyId, Long seasonId) {

//        a. 시즌 로드 + 회사 소유권 검증 (멀티테넌시)
        Season season = seasonRepository.findById(seasonId).orElseThrow(() -> new IllegalStateException("시즌 없음"));
        if (!season.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("접근 권한 없음");
        }

//        b. 스냅샷 파싱 -> gradeRules (라벨/비율/색상) 추출
        FormSnapshotDto snapshot;
        try {
            snapshot = objectMapper.readValue(season.getFormSnapshot(), FormSnapshotDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("규칙 스냅샷 파싱 실패", e);
        }
        List<FormSnapshotDto.GradeRule> gradeRules = snapshot.getGradeRules();

//        c. finalGrade 별 실제 인원 집계 (보정 반영 / null=미배분은 쿼리에서 제외)
//           DTO 리스트 -> Map<등급라벨, 실제인원> + 총 인원 누적
        Map<String, Integer> actualMap = new HashMap<>();
        int totalCount = 0;
        List<AutoGradeCountDto> grouped = evalGradeRepository.countByAutoGradeGroup(seasonId);
        for (AutoGradeCountDto dto : grouped) {
            int cnt = (int) dto.getCount();
            actualMap.put(dto.getLabel(), cnt);
            totalCount += cnt;
        }

//        d. 등급별 목표/실제 계산 (스냅샷 순서 유지)
//           - targetCount = total × ratio/100 반올림
//           - 마지막 등급은 잔여 인원 전부 (5번 applyDistribution 과 동일 규칙, 인원수 정합성)
        List<DistributionDiffDto.GradeDiff> diffList = new ArrayList<>();
        int assigned = 0;             // 지금까지 누적 할당된 목표 인원
        int mismatchCount = 0;        // 목표 != 실제 등급 수

        for (int gi = 0; gi < gradeRules.size(); gi++) {
            FormSnapshotDto.GradeRule g = gradeRules.get(gi);

//          목표 인원 (마지막 등급은 잔여 몰기)
            int targetCount;
            if (gi == gradeRules.size() - 1) {
                targetCount = totalCount - assigned;
            } else {
                targetCount = (int) Math.round(totalCount * g.getRatio().doubleValue() / 100.0);
                assigned += targetCount;
            }

//          실제 인원 (map 에 없으면 0 - 해당 등급 미배정 상태)
            Integer found = actualMap.get(g.getLabel());
            int actualCount = (found == null) ? 0 : found;

//           편차 + 상태 판정 (enum)
            int diff = actualCount - targetCount;
            DiffStatus status;
            if (diff == 0) {
                status = DiffStatus.MATCH;
            } else if (diff > 0) {
                status = DiffStatus.OVER;
                mismatchCount++;
            } else {
                status = DiffStatus.UNDER;
                mismatchCount++;
            }

//            d-4. 카드 1건 조립
            diffList.add(DistributionDiffDto.GradeDiff.builder()
                    .label(g.getLabel())
                    .color(g.getColor())
                    .targetRatio(g.getRatio())
                    .targetCount(targetCount)
                    .actualCount(actualCount)
                    .diff(diff)
                    .status(status)
                    .build());
        }

//        e. 현재 보정 건수 (isCalibrated=true row 수)
        int calibrationCount = (int) evalGradeRepository.countBySeason_SeasonIdAndIsCalibratedTrue(seasonId);

//        f. 최종 DTO 조립 후 반환
        return DistributionDiffDto.builder()
                .grades(diffList)
                .totalCount(totalCount)
                .mismatchCount(mismatchCount)
                .calibrationCount(calibrationCount)
                .isAllMatch(mismatchCount == 0)
                .build();
    }


    //    7. 보정 페이지 사원 목록 (페이징/필터/검색/정렬)
//       - autoGrade = 불변 원본 (그대로 읽음)
//       - finalGrade = 보정 반영된 현재 등급 (보정된 경우만 adjustedGrade 로 표시)
//       - 사유/수행자는 Calibration 이력에서 최신 1건만 조회 (전체 이력은 8번 API)
    @Transactional(readOnly = true)
    public Page<CalibrationListItemDto> getCalibrationList(UUID companyId, Long seasonId,
                                                           Long deptId, String keyword,
                                                           EvalGradeSortField sortField, Pageable pageable) {

//        a. 보정 대상 사원 페이지 조회 (autoGrade != null 만)
        Page<EvalGrade> gradePage = evalGradeRepository.searchCalibrationGrades(
                companyId, seasonId, deptId, keyword, sortField, pageable);

//        b. 보정된 row 의 gradeId 수집 -> Calibration 이력 batch 조회 (사유/수행자만 추출)
        List<Long> calibratedIds = new ArrayList<>();
        for (EvalGrade g : gradePage.getContent()) {
            if (Boolean.TRUE.equals(g.getIsCalibrated())) {
                calibratedIds.add(g.getGradeId());
            }
        }

//        최신 사유/수행자 Map 구성 (이력 시간순 조회 후 덮어쓰기로 마지막 값만 남김)
//        ※ 원본 등급은 이제 EvalGrade.autoGrade 가 불변이므로 이력 복원 불필요
        Map<Long, String> latestReasonMap = new HashMap<>();   // gradeId -> 최신 reason
        Map<Long, String> latestAdjusterMap = new HashMap<>(); // gradeId -> 최신 수행자 이름

        if (!calibratedIds.isEmpty()) {
            List<Calibration> histories =
                    calibrationRepository.findByGrade_GradeIdInOrderByCreatedAtAsc(calibratedIds);

            for (Calibration cal : histories) {
                Long gid = cal.getGrade().getGradeId();
//                매번 덮어쓰기 -> 루프 끝나면 마지막(최신) 값만 남음
                latestReasonMap.put(gid, cal.getReason());
                if (cal.getActor() != null) {
                    latestAdjusterMap.put(gid, cal.getActor().getEmpName());
                }
            }
        }

//        c. Entity -> DTO 변환
        List<CalibrationListItemDto> dtos = new ArrayList<>();
        for (EvalGrade g : gradePage.getContent()) {
            Long gid = g.getGradeId();
            boolean calibrated = Boolean.TRUE.equals(g.getIsCalibrated());

//            원본 등급: 항상 autoGrade (불변)
//            보정등급: 보정됐으면 현재 finalGrade, 아니면 null
            String originalGrade = g.getAutoGrade();
            String adjustedGrade = calibrated ? g.getFinalGrade() : null;

//            사유: 보정됐으면 최신 reason 1건만, 아니면 null
            String reason = calibrated ? latestReasonMap.get(gid) : null;

//            보정 수행자: 보정됐으면 최신 actor 이름, 아니면 null
            String adjusterName = calibrated ? latestAdjusterMap.get(gid) : null;

            dtos.add(CalibrationListItemDto.builder()
                    .gradeId(gid)
                    .empNum(g.getEmp().getEmpNum())
                    .name(g.getEmp().getEmpName())
                    .deptName(g.getDeptNameSnapshot())
                    .position(g.getPositionSnapshot())
                    .totalScore(g.getTotalScore())
                    .autoGrade(originalGrade)
                    .adjustedGrade(adjustedGrade)
                    .reason(reason)
                    .adjusterName(adjusterName)
                    .isCalibrated(calibrated)
                    .build());
        }

        return new PageImpl<>(dtos, pageable, gradePage.getTotalElements());
    }


    //    8. 보정 이력 조회 (시즌 전체, 건별 시간순)
//       - 같은 사원이라도 보정할 때마다 별도 row (A→B, B→S 각각 표시)
//       - createdAt 오름차순 → 프론트에서 index+1 로 순번 렌더
//       - 소유권 검증 후 Calibration 테이블에서 시즌 범위 조회
    @Transactional(readOnly = true)
    public List<CalibrationHistoryDto> getCalibrations(UUID companyId, Long seasonId) {

//        a. 소유권 검증
        Season season = seasonRepository.findById(seasonId).orElseThrow(() -> new IllegalStateException("시즌 없음"));
        if (!season.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("접근 권한 없음");
        }

//        b. 시즌 전체 보정 이력 조회 (createdAt 오름차순)
        List<Calibration> histories = calibrationRepository.findByGrade_Season_SeasonIdOrderByCreatedAtAsc(seasonId);

//        c. Entity -> DTO 변환
        List<CalibrationHistoryDto> dtos = new ArrayList<>();
        for (Calibration cal : histories) {
            EvalGrade grade = cal.getGrade();

            dtos.add(CalibrationHistoryDto.builder()
                    .calibrationId(cal.getCalibrationId())
                    .gradeId(grade.getGradeId())
                    .empNum(grade.getEmp().getEmpNum())
                    .empName(grade.getEmp().getEmpName())
                    .deptName(grade.getDeptNameSnapshot())
                    .fromGrade(cal.getFromGrade())
                    .toGrade(cal.getToGrade())
                    .reason(cal.getReason())
                    .adjusterName(cal.getActor() != null ? cal.getActor().getEmpName() : null)
                    .createdAt(cal.getCreatedAt())
                    .build());
        }

        return dtos;
    }

    //    9.일괄보정 저장
//    변경 반영 후 등급 분포 미리 계산 -> 목표 ratio와 일치하는지 검증 //통과 시 autoGrade덮기 +이전값 calibration이력 남기기
    public CalibrationBatchResultDto batchSaveCalibration(UUID companyId, Long adjusterEmpId, Long seasonId, List<CalibrationItemRequest> items) {

//   a. 시즌 로드 + 소유권/상태 검증
        Season season = seasonRepository.findById(seasonId).orElseThrow(() -> new IllegalStateException("시즌 없음"));

//        회사 소유권 검증 (멀티테넌시 — 다른 회사 시즌 접근 차단)
        if (!season.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("접근 권한 없음");
        }

//        이미 확정된 시즌은 보정 불가 (finalizedAt != null 이면 잠김 상태)
        if (season.getFinalizedAt() != null) {
            throw new IllegalStateException("확정된 시즌은 보정 불가");
        }

//        OPEN 상태만 보정 허용 (DRAFT/CLOSED 등은 차단)
        if (season.getStatus() != EvalSeasonStatus.OPEN) {
            throw new IllegalStateException("진행중 시즌만 보정 가능");
        }


//       b. 스냅샷 파싱 -> gradeRules 추출 (시즌 OPEN 시 박제된 병합 JSON)
        FormSnapshotDto snapshot;
        try {
            snapshot = objectMapper.readValue(season.getFormSnapshot(), FormSnapshotDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("규칙 스냅샷 파싱 실패", e);
        }
//        등급정의 목록 추출(비율 검증)
        List<FormSnapshotDto.GradeRule> gradeRules = snapshot.getGradeRules();

//        c.요청된 gradeId로 EvalGrade일괄 조회
//        요청 항목에서 gradeEd만 추출
        List<Long> gradeIds = new ArrayList<>();
        for (CalibrationItemRequest item : items) {
            gradeIds.add(item.getGradeId());
        }
//        db에서 해당 EvalGrade entity일괄 조회
        List<EvalGrade> gradeEntities = evalGradeRepository.findAllById(gradeIds);
//        Map<gradeId, EvalGrade>로 변환
        Map<Long, EvalGrade> gradeMap = new HashMap<>();
        for (EvalGrade g : gradeEntities) {
            gradeMap.put(g.getGradeId(), g);
        }

//        d.시뮬레이션: 변경 반영 후 분포 계산
//        현재 db의 autoGrade별 인원 집계 -> simMap(등급라벨 -> 인원수)
        Map<String, Integer> simMap = new HashMap<>();

//        autoGrade != null 인 전체 인원수 누적
        int totalCount = 0;
//        현재 분포 조회(6)
        List<AutoGradeCountDto> grouped = evalGradeRepository.countByAutoGradeGroup(seasonId);

//        조회 결과를 Map에 적재
        for (AutoGradeCountDto dto : grouped) {
            int cnt = (int) dto.getCount(); // 해당 등급 인원
            simMap.put(dto.getLabel(), cnt); //등급라벨 -> 인원수
            totalCount += cnt; //총 인원 누적
        }
//        요청 건별 시뮬레이션: from(현재등급) -1, to(새등급) +1
        for (CalibrationItemRequest item : items) {
//            gradeId로 entity조회
            EvalGrade g = gradeMap.get(item.getGradeId());
            if (g == null) {
                throw new IllegalArgumentException("존재하지 않는 등급입니다");
            }
//      현재 finalGrade = 변경 전 등급 (보정 반영된 현재 값)
            String fromGrade = g.getFinalGrade();
//            요청에서 받은 새등급
            String toGrade = item.getToGrade();
//            같은 등급이면 분포 변경 x(스킵)
            if (!fromGrade.equals(toGrade)) {
                simMap.put(fromGrade, simMap.getOrDefault(fromGrade, 0) - 1); //from에서 -1
                simMap.put(toGrade, simMap.getOrDefault(toGrade, 0) + 1); // to +1
            }
        }
//        e.비율 검증: target vs simulated
//        등급별 diff결과 리스트(실패 시 프론트에 반환)
        List<DistributionDiffDto.GradeDiff> diffList = new ArrayList<>();
//        해당 시점 까지 목표로 할당된 누적 인원
        int assigned = 0;
//        목표와 다른 등급 수 카운트
        int mismatchCount = 0;
//        등급규칙 순회(s,a,b,c,d순)
        for (int gi = 0; gi < gradeRules.size(); gi++) {
            FormSnapshotDto.GradeRule g = gradeRules.get(gi); //현재 등급 규칙

            int targetCount; //목표인원 계산

            if (gi == gradeRules.size() - 1) {//마지막 등급은 잔여 인원 모두
                targetCount = totalCount - assigned;
            } else {
                targetCount = (int) Math.round(totalCount * g.getRatio().doubleValue() / 100.0); //total x ratio/100 반올림
                assigned += targetCount; //누적 할당량 갱신
            }
//            시뮬레이션 결과에서 해당 등급 인원 조회
            Integer found = simMap.get(g.getLabel());
            int actualCount = (found == null) ? 0 : found;
//            편차 = 시뮬레이션 - 목표 인원
            int diff = actualCount - targetCount;
//            상태 판정(enum)
            DiffStatus status;
            if (diff == 0) {
                status = DiffStatus.MATCH;
            } else if (diff > 0) {
                status = DiffStatus.OVER;
                mismatchCount++;
            } else {
                status = DiffStatus.UNDER;
                mismatchCount++;
            }
//            diff항목 1건 조립
            diffList.add(DistributionDiffDto.GradeDiff.builder()
                    .label(g.getLabel())
                    .color(g.getColor())
                    .targetRatio(g.getRatio())
                    .targetCount(targetCount)
                    .actualCount(actualCount)
                    .diff(diff)
                    .status(status)
                    .build());
        }

//        f. 비율 불일치 -> 저장 없이 currentDiff 반환
//           프론트에서 어떤 등급이 몇 명 초과/부족인지 표시
        if (mismatchCount > 0) {
            return CalibrationBatchResultDto.builder()
                    .success(false)
                    .saveCount(0)                 // 저장 건수 0
                    .currentDiff(diffList)        // 불일치 상세
                    .build();
        }

//        g. 비율 일치 -> 실제 저장
//           보정 수행자 엔티티 조회 (Calibration.actor 에 기록)
        Employee adjuster = employeeRepository.findById(adjusterEmpId)
                .orElseThrow(() -> new IllegalStateException("보정 수행자가 없습니다"));

        int savedCount = 0;
//        요청 건별 처리
        for (CalibrationItemRequest item : items) {
//            대상 EvalGrade
            EvalGrade target = gradeMap.get(item.getGradeId());
//            변경 전 등급 (보정 반영된 현재 값 = finalGrade)
            String fromGrade = target.getFinalGrade();
//            변경 후 등급
            String toGrade = item.getToGrade();
//            같은 등급이면 스킵 (변경 없음 방어)
            if (fromGrade.equals(toGrade)) {
                continue;
            }
//            finalGrade 덮어쓰기 + isCalibrated = true (autoGrade 는 불변 원본 유지)
            target.applyCalibration(toGrade);
//            Calibration 이력 INSERT (건별 - 8번에서 전부 조회)
            Calibration cal = Calibration.builder()
                    .grade(target)
                    .fromGrade(fromGrade)
                    .toGrade(toGrade)
                    .reason(item.getReason())
                    .actor(adjuster)
                    .build();
            calibrationRepository.save(cal);
            savedCount++;
        }

//        h. 성공 결과 반환
        return CalibrationBatchResultDto.builder()
                .success(true)
                .saveCount(savedCount)
                .currentDiff(null)
                .build();
    }


    //    10. 최종 확정 페이지 상단 요약 지표
//       - 배정/미산정/보정 카운트 4개 (잠금 상태는 시즌 스토어에서 별도 로드)
    @Transactional(readOnly = true)
    public FinalizeSummaryDto getFinalizeSummary(UUID companyId, Long seasonId) {
        Season season = seasonRepository.findById(seasonId).orElseThrow(() -> new IllegalStateException("시즌 없음"));
        if (!season.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("접근 권한 없음");
        }

        long total= evalGradeRepository.countBySeason_SeasonId(seasonId);
        long assigned= evalGradeRepository.countBySeason_SeasonIdAndFinalGradeNotNull(seasonId);
        long calibrated= evalGradeRepository.countBySeason_SeasonIdAndIsCalibratedTrue(seasonId);

        return FinalizeSummaryDto.builder()
                .totalCount((int) total)
                .assignedCount((int) assigned)
                .unassignedCount((int) (total - assigned))
                .calibratedCount((int) calibrated)
                .build();
    }


    //    11. 최종 확정 페이지 미제출·미산정 직원 목록 (finalGrade IS NULL 대상)
    @Transactional(readOnly = true)
    public Page<UnassignedEmployeeDto> getUnassignedList(UUID companyId, Long seasonId,
                                                          Long deptId,
                                                          EvalGradeSortField sortField,
                                                          Pageable pageable) {
        return evalGradeRepository.searchUnassigned(companyId, seasonId, deptId, sortField, pageable);
    }

    
//    12. 최종 확정 및 잠금
    public FinalizeDto finalize(UUID companyId, Long adjusterEmpId, Long seasonId, FinalizeDto request) {

        Season season = seasonRepository.findById(seasonId).orElseThrow(() -> new IllegalStateException("시즌이 없습니다"));
        if (!season.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다");
        }
        if (season.getFinalizedAt() != null) {
            throw new IllegalStateException("이미 확정된 시즌입니다");
        }
        if (season.getStatus() != EvalSeasonStatus.OPEN) {
            throw new IllegalStateException("진행중인 시즌만 확정 가능합니다");
        }

//        미산정자 ack리스트 포함여부 검증
        List<Long> unassignedEmpIds = evalGradeRepository.findUnassignedEmpIds(seasonId);
        Set<Long> ackSet = new HashSet<>(
                request.getAcknowledgedEmpIds() != null ? request.getAcknowledgedEmpIds() : Collections.emptyList());

//        미체크된 미산정자 남을 시 확정 거부
        for (Long empId : unassignedEmpIds) {
            if (!ackSet.contains(empId)) {
                throw new IllegalStateException("미산정자 전원 확인이 필요합니다");
            }
        }

//        확정-EvalGrade전체 잠금 + 시즌 finalizedAt세팅 + status CLOSED 전환
//        (status=CLOSED 로 변경)
        LocalDateTime now = LocalDateTime.now();
        int lockedCount = evalGradeRepository.lockAllAssigned(seasonId, now);
        season.markFinalized(now);
        season.close();

        return FinalizeDto.builder()
                .finalizedAt(now)
                .lockedCount(lockedCount)
                .build();
    }


    //    13. 평가 결과 목록 ( 진행중/완료 시즌만 조회, 미산정자 포함)
//       - 진행중: finalGrade = 보정까지 반영된 현재 값 (실시간)
//       - 완료: finalGrade = 박제된 최종 값
//       - unscoredOnly: null=전체 / true=미산정자만 / false=산정자만
    @Transactional(readOnly = true)
    public Page<FinalGradeListItemDto> getFinalList(UUID companyId, Long seasonId,
                                                    Long deptId, String keyword,
                                                    Boolean unscoredOnly,
                                                    EvalGradeSortField sortField, Pageable pageable) {

//        시즌 로드 + 회사 소유권 검증 (멀티테넌시)
        Season season = seasonRepository.findById(seasonId).orElseThrow(() -> new IllegalStateException("시즌 없음"));
        if (!season.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("접근 권한 없음");
        }

//        시즌 상태 검증 - DRAFT(준비중) 시즌은 조회 불가
        if (season.getStatus() == EvalSeasonStatus.DRAFT) {
            throw new IllegalStateException("준비중 시즌은 결과 조회 불가");
        }

//        Impl 에서 필터 + DTO 변환까지 수행 (1번 searchDraftList 와 동일 패턴)
        return evalGradeRepository.searchFinalList(
                companyId, seasonId, deptId, keyword, unscoredOnly, sortField, pageable);
    }

}

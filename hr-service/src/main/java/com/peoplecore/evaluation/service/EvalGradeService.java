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
import com.peoplecore.evaluation.domain.Goal;
import com.peoplecore.evaluation.domain.GoalApprovalStatus;
import com.peoplecore.evaluation.domain.GoalType;
import com.peoplecore.evaluation.domain.KpiDirection;
import com.peoplecore.evaluation.domain.ManagerEvaluation;
import com.peoplecore.evaluation.domain.MyResultStatus;
import com.peoplecore.evaluation.domain.SelfEvalApprovalStatus;
import com.peoplecore.evaluation.domain.SelfEvaluation;
import com.peoplecore.evaluation.domain.SelfEvaluationFile;
import com.peoplecore.evaluation.dto.*;
import com.peoplecore.evaluation.domain.Stage;
import com.peoplecore.evaluation.domain.StageStatus;
import com.peoplecore.evaluation.domain.StageType;
import com.peoplecore.alarm.publisher.HrAlarmPublisher;
import com.peoplecore.event.AlarmEvent;
import com.peoplecore.evaluation.repository.CalibrationRepository;
import com.peoplecore.evaluation.repository.EvalGradeRepository;
import com.peoplecore.evaluation.repository.GoalRepository;
import com.peoplecore.evaluation.repository.ManagerEvaluationRepository;
import com.peoplecore.evaluation.repository.SeasonRepository;
import com.peoplecore.evaluation.repository.SelfEvaluationFileRepository;
import com.peoplecore.evaluation.repository.SelfEvaluationRepository;
import com.peoplecore.evaluation.repository.StageRepository;
import com.peoplecore.attendance.entity.WorkStatus;
import com.peoplecore.attendance.repository.CommuteRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;


// 등급 - 초안 자동 산정, 보정, 최종 확정, 결과 조회

@Service
@Transactional
public class EvalGradeService {

    // 자기평가 만점 — 100점 고정 (회사별 설정 없음)
    private static final BigDecimal SCALE_TO = BigDecimal.valueOf(100);

    private final EvalGradeRepository evalGradeRepository;
    private final SeasonRepository seasonRepository;
    private final DepartmentRepository departmentRepository;
    private final CalibrationRepository calibrationRepository;
    private final StageRepository stageRepository;
    private final ObjectMapper objectMapper;
    private final EmployeeRepository employeeRepository;
    private final GoalRepository goalRepository;
    private final SelfEvaluationRepository selfEvaluationRepository;
    private final SelfEvaluationFileRepository selfEvaluationFileRepository;
    private final ManagerEvaluationRepository mgrEvalRepository;
    private final HrAlarmPublisher hrAlarmPublisher;
    private final CommuteRecordRepository commuteRecordRepository;

    public EvalGradeService(EvalGradeRepository evalGradeRepository,
                            SeasonRepository seasonRepository,
                            DepartmentRepository departmentRepository,
                            CalibrationRepository calibrationRepository,
                            StageRepository stageRepository,
                            ObjectMapper objectMapper,
                            EmployeeRepository employeeRepository,
                            GoalRepository goalRepository,
                            SelfEvaluationRepository selfEvaluationRepository,
                            SelfEvaluationFileRepository selfEvaluationFileRepository,
                            ManagerEvaluationRepository mgrEvalRepository,
                            HrAlarmPublisher hrAlarmPublisher,
                            CommuteRecordRepository commuteRecordRepository) {
        this.evalGradeRepository = evalGradeRepository;
        this.seasonRepository = seasonRepository;
        this.departmentRepository = departmentRepository;
        this.calibrationRepository = calibrationRepository;
        this.stageRepository = stageRepository;
        this.objectMapper = objectMapper;
        this.employeeRepository = employeeRepository;
        this.goalRepository = goalRepository;
        this.selfEvaluationRepository = selfEvaluationRepository;
        this.selfEvaluationFileRepository = selfEvaluationFileRepository;
        this.mgrEvalRepository = mgrEvalRepository;
        this.hrAlarmPublisher = hrAlarmPublisher;
        this.commuteRecordRepository = commuteRecordRepository;
    }

    @Transactional(readOnly = true)
    // 1. 자동 산정 대상 목록 조회 //DB totalScore + autoGrade 그대로 반환 // - totalScore/autoGrade 가 null일시 미산정 -> 프론트 "-" 렌더
    //  부서/직급 시즌 오픈 시점 스냅샷 (조직개편 무관)
    public Page<DraftListItemDto> getDraftList(UUID companyId, Long seasonId, Long deptId, String keyword, EvalGradeSortField sortField, Sort.Direction sortDirection, Pageable pageable) {
        return evalGradeRepository.searchDraftList(companyId, seasonId, deptId, keyword, sortField, sortDirection, pageable);
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
//          - self 는 clip 전 raw (rawSelfScore) + clip 후 (selfScore) 분리 저장
//            → 등급 보정 단계에서 "자기평가 scaleTo 초과로 잘린 사원" 식별에 사용
        for (EvalGrade row : rows) {
            Long empId = row.getEmp().getEmpId();

//            items순회로 가중평균 누적식으로 계산
//            - 자기평가(self), 상위자평가(manager) 점수는 별도 변수에 따로 보관
//              -> 이후 applyBiasAdjustment에서 상위자점수에만 Z-score 보정을 적용하려
            BigDecimal weightedSum = BigDecimal.ZERO;
            BigDecimal weightTotal = BigDecimal.ZERO;
            BigDecimal rawSelfScore = null;  // 자기평가 clip 전 가중평균 (scaleTo 초과 판정용)
            BigDecimal selfScore = null;     // 자기평가 원점수 (scaleTo 상한 clip 됨, 편향보정 제외)
            BigDecimal managerScore = null;  // 상위자평가 원점수 (편향보정 대상)
            boolean missing = false;

            for (FormSnapshotDto.Item item : snapshot.getItemList()) {
                // 비활성화된 잠금 항목(enabled=false)은 집계 대상에서 제외
                if (isItemDisabled(item)) continue;

                BigDecimal score;
                if ("self".equals(item.getId())) {
                    // self 는 clip 전/후 둘 다 필요해서 SelfScoreResult 로 받음
                    SelfScoreResult self = aggregateSelfScore(empId, seasonId, snapshot);
                    if (self == null) { missing = true; break; }
                    rawSelfScore = self.raw;
                    selfScore = self.clipped;
                    score = self.clipped;
                } else if ("manager".equals(item.getId())) {
                    score = aggregateManagerScore(empId, seasonId, snapshot);
                    if (score == null) { missing = true; break; }
                    managerScore = score;
                } else {
                    // 알 수 없는 item (현재 범위 밖) -> missing
                    missing = true;
                    break;
                }

                weightedSum = weightedSum.add(score.multiply(item.getWeight())); //점수 x가중치 누적합
                weightTotal = weightTotal.add(item.getWeight()); //가중치 합(프론트로 정합성 100)
            }
            if (missing || weightTotal.signum() == 0) {
//                재산정 시 미제출이면 이전 점수 전부 null 로 초기화 (유령값 방지)
                row.applyTotalScore(null, null, null, null, null, null);
                continue;
            }
//            가중평균
            BigDecimal weighted = weightedSum.divide(weightTotal, 2, RoundingMode.HALF_UP);


//            가감점 기능 제거 — 2026-04
//            조정점수(근태 등) - 시즌 기간 내 지각/결근 카운트 × 항목별 점수
//            BigDecimal adjustment = aggregateAdustment(empId, companyId,
//                    season.getStartDate(), season.getEndDate(), snapshot);
            BigDecimal adjustment = BigDecimal.ZERO;

//            종합점수
            BigDecimal total = weighted.add(adjustment);

//            update(dirty checking) - raw/clipped self + manager 원점수 + 가중/가감/종합 저장
            row.applyTotalScore(rawSelfScore, selfScore, managerScore, weighted, adjustment, total);
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

    //    자기평가 점수 집계 결과 - clip 전 raw + clip 후 clipped 둘 다 보관
    //      - raw    : goal.weight 가중평균 [0, cap] (DB 저장용 — 등급 보정 후보 판정)
    //      - clipped: scaleTo 상한으로 자른 값 [0, scaleTo] (실제 점수 집계에 사용)
    private static class SelfScoreResult {
        final BigDecimal raw;
        final BigDecimal clipped;
        SelfScoreResult(BigDecimal raw, BigDecimal clipped) {
            this.raw = raw;
            this.clipped = clipped;
        }
    }

    //    자기평가 점수 집계 - KPI Goal만 사용 (OKR 제외)
    //      1) 각 KPI rate = computeAchievementRate(direction, target, actual, cap, tolerance)
    //      2) per-goal score: clamp(rate, 0, cap) + 미달 패널티 (threshold > 0 && rate < threshold 면 × factor)
    //      3) goal.weight (사원이 입력한 %) 가중평균 → raw [0, cap]
    //      4) scaleTo 상한 clip → clipped [0, scaleTo]
    //      cap 은 "초과 달성이 미달 커버" 버퍼, scaleTo 는 자기평가 최종 만점
    //      - 승인 KPI 0개, SelfEvaluation 미제출/actualValue null, rate 불능 시 null 반환 (부모 missing 처리)
    private SelfScoreResult aggregateSelfScore(Long empId, Long seasonId, FormSnapshotDto snapshot) {
        List<Goal> goals = goalRepository.findByEmp_EmpIdAndSeason_SeasonIdAndApprovalStatusOrderByGoalIdDesc(
                empId, seasonId, GoalApprovalStatus.APPROVED);

        List<Goal> kpiGoals = new ArrayList<>();
        for (Goal g : goals) {
            if (g.getGoalType() == GoalType.KPI) kpiGoals.add(g);
        }
        if (kpiGoals.isEmpty()) return null;

        List<Long> goalIds = new ArrayList<>();
        for (Goal g : kpiGoals) goalIds.add(g.getGoalId());
        List<SelfEvaluation> selfEvals = selfEvaluationRepository.findByGoal_GoalIdIn(goalIds);
        Map<Long, SelfEvaluation> selfByGoal = new HashMap<>();
        for (SelfEvaluation se : selfEvals) selfByGoal.put(se.getGoal().getGoalId(), se);

        // kpiScoring 설정 추출 (없으면 업계 표준 기본값) — 자기평가 만점은 SCALE_TO 상수 (100 고정)
        BigDecimal cap = BigDecimal.valueOf(120);
        BigDecimal tolerance = BigDecimal.ZERO;
        BigDecimal threshold = BigDecimal.ZERO;
        BigDecimal factor = BigDecimal.ONE;
        FormSnapshotDto.KpiScoring scoring = snapshot.getKpiScoring();
        if (scoring != null) {
            if (scoring.getCap() != null) cap = scoring.getCap();
            if (scoring.getMaintainTolerance() != null) tolerance = scoring.getMaintainTolerance();
            if (scoring.getUnderperformanceThreshold() != null) threshold = scoring.getUnderperformanceThreshold();
            if (scoring.getUnderperformanceFactor() != null) factor = scoring.getUnderperformanceFactor();
        }

        BigDecimal weightedSum = BigDecimal.ZERO;
        int totalWeight = 0;
        for (Goal g : kpiGoals) {
            SelfEvaluation se = selfByGoal.get(g.getGoalId());
            // 미제출(row 없음) / actualValue 미입력 → 점수 산정 제외 (전체 missing 처리)
            if (se == null || se.getActualValue() == null) return null;
            // 자기평가 상태 가드 — APPROVED 만 점수 인정
            //   DRAFT(임시저장) / PENDING(상위자 미응답) / REJECTED(상위자 반려) → 산정 제외
            //   기록은 그대로 보존, 자동산정에서만 빼는 정책
            if (se.getApprovalStatus() != SelfEvalApprovalStatus.APPROVED) return null;

            BigDecimal rate = computeAchievementRate(g.getKpiDirection(), g.getTargetValue(), se.getActualValue(), cap, tolerance);
            if (rate == null) return null;

            // per-goal 점수: [0, cap] 범위로 자름 + 미달 패널티 (scaleTo clip 은 집계 후 한 번)
            BigDecimal capped = rate.max(BigDecimal.ZERO).min(cap);
            BigDecimal goalScore = capped;
            if (threshold.signum() > 0 && rate.compareTo(threshold) < 0) {
                goalScore = goalScore.multiply(factor);
            }

            // KPI 만 도는 루프 안이고 KPI 는 weight 가 항상 박혀있음 (디폴트 10) — null 가드 불필요
            int tw = g.getWeight();
            weightedSum = weightedSum.add(goalScore.multiply(BigDecimal.valueOf(tw)));
            totalWeight += tw;
        }
        if (totalWeight == 0) return null;

        // raw: weight 가중평균 [0, cap] — 합이 항상 100이라 분모 100 (분포 정규화 동일 효과)
        BigDecimal raw = weightedSum.divide(BigDecimal.valueOf(totalWeight), 2, RoundingMode.HALF_UP);
        // clipped: 자기평가 만점(100) 상한으로 자름 — 실제 점수 집계용
        BigDecimal clipped = raw.min(SCALE_TO).setScale(2, RoundingMode.HALF_UP);
        return new SelfScoreResult(raw, clipped);
    }

    //    상위자평가 점수 집계 - gradeLabel(S/A/B/C/D) -> snapshot.rawScoreTable 룩업
    //      - ManagerEvaluation row 없음, gradeLabel null, rawScoreTable 에 매칭 없음 시 null
    private BigDecimal aggregateManagerScore(Long empId, Long seasonId, FormSnapshotDto snapshot) {
        ManagerEvaluation mgrEval = mgrEvalRepository.findByEmployee_EmpIdAndSeason_SeasonId(empId, seasonId).orElse(null);
        if (mgrEval == null || mgrEval.getGradeLabel() == null) return null;
        if (snapshot.getRawScoreTable() == null) return null;

        String label = mgrEval.getGradeLabel();
        for (FormSnapshotDto.RawScore row : snapshot.getRawScoreTable()) {
            if (label.equals(row.getGradeId())) {
                return row.getRawScore();
            }
        }
        return null;
    }

    // 가감점 기능 제거 — 2026-04
    //    조정점수 집계 - enabled 항목만 합산
    //      - 'late'   : workStatus IN (LATE, LATE_AND_EARLY) 카운트
    //      - 'absent' : workStatus = ABSENT 카운트
    //    threshold 면제 횟수까지는 무감점, 초과분(count - threshold)에만 points 적용.
//    private BigDecimal aggregateAdustment(Long empId, UUID companyId,
//                                          LocalDate from, LocalDate to,
//                                          FormSnapshotDto snapshot) {
//        BigDecimal sum = BigDecimal.ZERO;
//        if (from == null || to == null) return sum;
//
//        for (FormSnapshotDto.Adjustment adj : snapshot.getAdjustments()) {
//            if (!Boolean.TRUE.equals(adj.getEnabled())) continue;
//
//            long count;
//            if ("late".equals(adj.getId())) {
//                count = countLate(companyId, empId, from, to);
//            } else if ("absent".equals(adj.getId())) {
//                count = countAbsent(companyId, empId, from, to);
//            } else {
//                count = 0;
//            }
//
//            long threshold = adj.getThreshold() != null ? Math.max(0, adj.getThreshold()) : 0;
//            long effective = Math.max(0, count - threshold);
//
//            if (effective > 0) {
//                sum = sum.add(adj.getPoints().multiply(BigDecimal.valueOf(effective)));
//            }
//        }
//        return sum;
//    }
//
//    //    지각 카운트 - LATE_AND_EARLY (지각+조퇴) 도 지각 1회로 집계
//    private long countLate(UUID companyId, Long empId, LocalDate from, LocalDate to) {
//        return commuteRecordRepository
//                .countByCompanyIdAndEmployee_EmpIdAndWorkDateBetweenAndWorkStatusIn(
//                        companyId, empId, from, to,
//                        EnumSet.of(WorkStatus.LATE, WorkStatus.LATE_AND_EARLY));
//    }
//
//    //    결근 카운트 - 배치가 미출근일에 CommuteRecord.absent() 로 row 적재
//    private long countAbsent(UUID companyId, Long empId, LocalDate from, LocalDate to) {
//        return commuteRecordRepository
//                .countByCompanyIdAndEmployee_EmpIdAndWorkDateBetweenAndWorkStatusIn(
//                        companyId, empId, from, to,
//                        EnumSet.of(WorkStatus.ABSENT));
//    }


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
        // 가감점 기능 제거 — 2026-04
        // BigDecimal adjustment = row.getAdjustmentScore() != null
        //         ? row.getAdjustmentScore()
        //         : BigDecimal.ZERO;
        BigDecimal adjustment = BigDecimal.ZERO;
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
//       경계 동점자(biasAdjustedScore 동일)는 상위 등급으로 흡수 → 비율 초과분은 보정 단계에서 조정
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
            int prevIdx = idx;
            for (int i = 0; i < quota && idx < total; i++, idx++) {
                ranked.get(idx).applyDistribution(g.getLabel(), idx + 1);
            }

            // 경계 동점자 흡수 (마지막 등급 제외) — 컷오프 인원과 biasAdjustedScore 동일하면 같은 등급으로 끌어올림
            if (gi < gradeRules.size() - 1 && idx > prevIdx && idx < total) {
                BigDecimal cutoffScore = ranked.get(idx - 1).getBiasAdjustedScore();
                while (idx < total && ranked.get(idx).getBiasAdjustedScore().compareTo(cutoffScore) == 0) {
                    ranked.get(idx).applyDistribution(g.getLabel(), idx + 1);
                    idx++;
                }
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


    //    4. 등급 보정 검토 대상 조회 (프론트 GradeCalibration 화면 진입 시 호출)
//       - 편향보정 스킵된 팀 (전원 동점 / 소규모) - Z-score 보정 불가
//       - 자기평가 scaleTo 초과로 clip 된 사원 - 등급 상향 후보
//       DB 스냅샷(teamStdDev/teamSize/rawSelfScore)을 읽어 복원 -> 단순 조회
    @Transactional(readOnly = true)
    public CalibrationReviewDto getCalibrationReview(UUID companyId, Long seasonId) {

//        a. 시즌 로드 + 소유권 검증
        Season season = seasonRepository.findById(seasonId).orElseThrow(() -> new IllegalStateException("시즌 없음"));
        if (!season.getCompany().getCompanyId().equals(companyId)) {    // 회사 소유권 검증
            throw new IllegalArgumentException("접근 권한 없음");
        }

//        b. 스냅샷에서 minTeamSize 추출 - 보정 당시 기준과 동일 기준 사용
//           자기평가 만점은 SCALE_TO 상수 (100 고정) 사용
        int minTeamSize = 5;                                            // 기본값
        if (season.getFormSnapshot() != null) {
            try {
                FormSnapshotDto snap = objectMapper.readValue(season.getFormSnapshot(), FormSnapshotDto.class);           // JSON 파싱
                if (snap.getMinTeamSize() != null) {
                    minTeamSize = snap.getMinTeamSize();                // 스냅샷 값으로 덮어씀
                }
            } catch (JsonProcessingException ignored) {
//                파싱 실패해도 기본값으로 조회 계속 진행
            }
        }

//        c. 해당 시즌의 모든 EvalGrade 레코드 조회
        List<EvalGrade> rows = evalGradeRepository.findBySeason_SeasonId(seasonId);

//        d. 이상 팀 / clip 대상 수집용 변수
        Set<Long> zeroStdDevTeams = new LinkedHashSet<>();  // 전원 동점 팀 ID (중복 방지 + 순서 유지)
        Set<Long> undersizedTeams = new LinkedHashSet<>();  // 소규모 팀 ID
        Set<Long> seenDepts = new HashSet<>();              // 팀당 한 번만 검사
        int processedCount = 0;                             // 보정 처리된 인원 수 (0이면 미실행)
        List<CalibrationReviewDto.ClippedSelfEmployeeDto> clippedSelfEmployees = new ArrayList<>();

//        e. 레코드 순회 - 이상 팀 판정 + clip 대상 수집
        for (EvalGrade row : rows) {
//            biasAdjustedScore 가 null 아니면 보정 실행된 인원 -> 카운트
            if (row.getBiasAdjustedScore() != null) {
                processedCount++;
            }

//            자기평가 clip 대상 판정: rawSelfScore > 자기평가 만점(SCALE_TO=100)
//              - rawSelfScore == null 이면 자동산정 미실행/미제출 -> 스킵
            if (row.getRawSelfScore() != null && row.getRawSelfScore().compareTo(SCALE_TO) > 0) {
                clippedSelfEmployees.add(CalibrationReviewDto.ClippedSelfEmployeeDto.builder()
                        .empId(row.getEmp() != null ? row.getEmp().getEmpId() : null)
                        .empName(row.getEmp() != null ? row.getEmp().getEmpName() : null)
                        .deptName(row.getDeptNameSnapshot())
                        .rawSelfScore(row.getRawSelfScore())
                        .selfScore(row.getSelfScore())
                        .autoGrade(row.getAutoGrade())
                        .build());
            }

            Long deptId = row.getDeptIdSnapshot();                      // 당시 소속 팀 ID (박제값)

//            deptId 없거나 이미 검사한 팀이면 팀 판정만 스킵 (팀당 한 번)
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
        return CalibrationReviewDto.builder()
                .seasonId(seasonId)                                     // 시즌 ID
                .processedCount(processedCount)                         // 처리 인원수
                .zeroStdDevTeams(buildTeamInfos(zeroStdDevTeams))       // 동점 팀 목록 (DTO)
                .undersizedTeams(buildTeamInfos(undersizedTeams))       // 소규모 팀 목록 (DTO)
                .clippedSelfEmployees(clippedSelfEmployees)             // 자기평가 clip 대상 사원
                .build();
    }


    //    5. 이상보정조회 부서id집합 -> TeamAnomalyDto리스트 변환, batch조회
    private List<CalibrationReviewDto.TeamAnomalyDto> buildTeamInfos(Collection<Long> deptIds) {
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
        List<CalibrationReviewDto.TeamAnomalyDto> result = new ArrayList<>();
        for (Long id : deptIds) {
            Department d = deptMap.get(id);

            if (d != null) {
//                조회 성공 시 정상 반환
                result.add(CalibrationReviewDto.TeamAnomalyDto.from(d));
            } else {
//                조회 실패 시 ->폴백 dto
                result.add(CalibrationReviewDto.TeamAnomalyDto.ofMissing(id));
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
                                                           EvalGradeSortField sortField, Sort.Direction sortDirection, Pageable pageable) {

//        a. 보정 대상 사원 페이지 조회 (autoGrade != null 만)
        Page<EvalGrade> gradePage = evalGradeRepository.searchCalibrationGrades(
                companyId, seasonId, deptId, keyword, sortField, sortDirection, pageable);

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
                    .totalScore(g.getBiasAdjustedScore()) // Z-score 보정 후 점수 (= 등급 배분 기준)
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
//            - MATCH: 정확히 일치
//            - OVER: 초과 (상위 등급은 차단, 마지막 등급은 잔여 흡수 역할이라 허용)
//            - UNDER: 부족 — 자동 산정 시 동점자 상위 흡수로 자연 발생, 정책상 허용
            DiffStatus status;
            boolean isLast = (gi == gradeRules.size() - 1);
            if (diff == 0) {
                status = DiffStatus.MATCH;
            } else if (diff > 0) {
                status = DiffStatus.OVER;
                if (!isLast) mismatchCount++;   // 마지막 등급 OVER는 허용
            } else {
                status = DiffStatus.UNDER;      // 전체 허용
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
                                                          Sort.Direction sortDirection,
                                                          Pageable pageable) {
        return evalGradeRepository.searchUnassigned(companyId, seasonId, deptId, sortField, sortDirection, pageable);
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

//        확정+알림 - 수동/자동 공통 처리 (스케줄러에서도 호출)
        int lockedCount = finalizeAndNotify(companyId, seasonId, season);

        return FinalizeDto.builder()
                .finalizedAt(season.getFinalizedAt())
                .lockedCount(lockedCount)
                .build();
    }


//    시즌 확정 + 사원 전원 알림 발행 (수동 finalize / 스케줄러 자동 확정 공통)
//    - EvalGrade 전체 잠금 + finalizedAt + status CLOSED 전환
//    - 시즌 대상 사원 전원에게 "최종 평가결과 공개" 알림
    public int finalizeAndNotify(UUID companyId, Long seasonId, Season season) {
        LocalDateTime now = LocalDateTime.now();
        int lockedCount = evalGradeRepository.lockAllAssigned(seasonId, now);
        season.markFinalized(now);
        season.close();

        List<Long> empIds = evalGradeRepository.findEmpIdsBySeason(seasonId);
        if (!empIds.isEmpty()) {
            hrAlarmPublisher.publisher(AlarmEvent.builder()
                    .companyId(companyId)
                    .empIds(empIds)
                    .alarmType("EVAL")
                    .alarmTitle(season.getName())
                    .alarmContent("최종 평가결과가 공개되었습니다")
                    .alarmLink("/eval/my/result")
                    .alarmRefType("SEASON")
                    .alarmRefId(seasonId)
                    .build());
        }
        return lockedCount;
    }


    //    13. 평가 결과 목록 ( 진행중/완료 시즌만 조회, 미산정자 포함)
//       - 진행중: finalGrade = 보정까지 반영된 현재 값 (실시간)
//       - 완료: finalGrade = 박제된 최종 값
//       - unscoredOnly: null=전체 / true=미산정자만 / false=산정자만
    @Transactional(readOnly = true)
    public Page<FinalGradeListItemDto> getFinalList(UUID companyId, Long seasonId,
                                                    Long deptId, String keyword,
                                                    Boolean unscoredOnly,
                                                    EvalGradeSortField sortField, Sort.Direction sortDirection, Pageable pageable) {

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
                companyId, seasonId, deptId, keyword, unscoredOnly, sortField, sortDirection, pageable);
    }


    //    15. 평가 결과 상세 (HR 전용) - 한 사원의 단계별 타임라인 전체
    //       - gradeId = EvalGrade PK
    //       - 실시간 JOIN + 스냅샷 컬럼 사용. 미진행 단계는 null/빈 배열
    @Transactional(readOnly = true)
    public EvalGradeDetailDto getDetail(UUID companyId, Long gradeId) {

//        EvalGrade 로드 + 회사 소유권 검증
//        조회 1 - 평가 등급 row 전체 (점수/등급/스냅샷 컬럼)
        EvalGrade g = evalGradeRepository.findById(gradeId).orElseThrow(() -> new IllegalArgumentException("등급 정보를 찾을 수 없습니다"));
        if (!g.getEmp().getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("접근 권한 없음");
        }
        Season season = g.getSeason();
        Long seasonId = season.getSeasonId();
        Long empId = g.getEmp().getEmpId();

//        스냅샷 파싱 (item weight 추출용)
        FormSnapshotDto snapshot = null;
        if (season.getFormSnapshot() != null) {
            try {
                snapshot = objectMapper.readValue(season.getFormSnapshot(), FormSnapshotDto.class);
            } catch (JsonProcessingException ignored) { }
        }

//       1 - 목표등록: 승인된 목표 + 비율
//       조회 - 승인된 목표 목록
        List<Goal> goals = goalRepository.findByEmp_EmpIdAndSeason_SeasonIdAndApprovalStatusOrderByGoalIdDesc(empId, seasonId, GoalApprovalStatus.APPROVED);
        List<Long> goalIds = new ArrayList<>();
        for (Goal go : goals) {
            goalIds.add(go.getGoalId());
        }

        // weight 는 Goal 자체에 박제되어 있으므로 별도 계산 불필요 (OKR 은 null)
        List<EvalGradeDetailDto.GoalEntry> goalDtos = new ArrayList<>();
        for (Goal go : goals) {
            goalDtos.add(EvalGradeDetailDto.GoalEntry.builder()
                    .goalType(go.getGoalType())
                    .category(go.getCategory())
                    .title(go.getTitle())
                    .weight(go.getWeight())
                    .targetValue(go.getTargetValue())
                    .targetUnit(go.getTargetUnit())
                    .build());
        }

//       2a - 평가입력내역: itemScores (자기/상위자 요약)
        List<EvalGradeDetailDto.ItemScore> itemScores = new ArrayList<>();
        if (snapshot != null && snapshot.getItemList() != null) {
            for (FormSnapshotDto.Item it : snapshot.getItemList()) {
                if (Boolean.TRUE.equals(it.getLocked()) && Boolean.FALSE.equals(it.getEnabled())) continue;
                BigDecimal score = null;
                if ("self".equals(it.getId())) score = g.getSelfScore();
                else if ("manager".equals(it.getId())) score = g.getManagerScore();
                itemScores.add(EvalGradeDetailDto.ItemScore.builder()
                        .itemId(it.getId())
                        .itemName(it.getName())
                        .score(score)
                        .weight(it.getWeight())
                        .build());
            }
        }

//         2b - 자기평가 상세 + 파일
//         조회 - 승인된 목표들의 자기평가 일괄
        List<SelfEvaluation> selfEvals = goalIds.isEmpty() ? new ArrayList<>() : selfEvaluationRepository.findByGoal_GoalIdIn(goalIds);
        Map<Long, SelfEvaluation> selfByGoal = new HashMap<>();
        List<Long> selfEvalIds = new ArrayList<>();
        for (SelfEvaluation se : selfEvals) {
            selfByGoal.put(se.getGoal().getGoalId(), se);
            selfEvalIds.add(se.getSelfEvalId());
        }
        Map<Long, List<SelfEvaluationFile>> filesBySelfEvalId = new HashMap<>();
        if (!selfEvalIds.isEmpty()) {
//            조회 - 자기평가 근거 파일 일괄
            List<SelfEvaluationFile> allFiles = selfEvaluationFileRepository.findBySelfEvaluation_SelfEvalIdIn(selfEvalIds);
            for (SelfEvaluationFile f : allFiles) {
                Long sid = f.getSelfEvaluation().getSelfEvalId();
                List<SelfEvaluationFile> list = filesBySelfEvalId.get(sid);
                if (list == null) { list = new ArrayList<>(); filesBySelfEvalId.put(sid, list); }
                list.add(f);
            }
        }

        List<EvalGradeDetailDto.SelfEvalEntry> selfEntries = new ArrayList<>();
        for (Goal go : goals) {
            SelfEvaluation se = selfByGoal.get(go.getGoalId());
            if (se == null) continue;
            if (se.getApprovalStatus() != SelfEvalApprovalStatus.APPROVED) continue;
            List<SelfEvaluationFile> fs = filesBySelfEvalId.get(se.getSelfEvalId());
            List<SelfEvaluationResponse.FileResponse> fileDtos = new ArrayList<>();
            if (fs != null) {
                for (SelfEvaluationFile f : fs) {
                    fileDtos.add(SelfEvaluationResponse.FileResponse.from(f));
                }
            }
            selfEntries.add(EvalGradeDetailDto.SelfEvalEntry.builder()
                    .goalType(go.getGoalType())
                    .title(go.getTitle())
                    .weight(go.getWeight())
                    .targetValue(go.getTargetValue())
                    .targetUnit(go.getTargetUnit())
                    .actualValue(se.getActualValue())
                    .achievementLevel(se.getAchievementLevel())
                    .achievementDetail(se.getAchievementDetail())
                    .files(fileDtos)
                    .build());
        }

//       2c - 상위자평가 상세
//       조회 - 해당 사원이 받은 팀장평가 단건
        EvalGradeDetailDto.ManagerEvalEntry mgrDto = null;
        ManagerEvaluation mgrEval = mgrEvalRepository.findByEmployee_EmpIdAndSeason_SeasonId(empId, seasonId).orElse(null);

        if (mgrEval != null) {
            mgrDto = EvalGradeDetailDto.ManagerEvalEntry.builder()
                    .grade(mgrEval.getGradeLabel())
                    .comment(mgrEval.getComment())
                    .feedback(mgrEval.getFeedback())
                    .build();
        }

//        가감점 기능 제거 — 2026-04
//        2d 조정 항목 (TODO: 근태 발생건수 집계 구현 후)
//        List<EvalGradeDetailDto.AdjustmentItem> adjDtos = new ArrayList<>();

//        3 - 보정 이력
//        조회 - 해당 등급의 보정 이력 시간순 전체
        List<Calibration> calibs = calibrationRepository.findByGrade_GradeIdInOrderByCreatedAtAsc(List.of(gradeId));
        List<EvalGradeDetailDto.CalibrationEntry> calibDtos = new ArrayList<>();
        for (Calibration c : calibs) {
            calibDtos.add(EvalGradeDetailDto.CalibrationEntry.builder()
                    .date(c.getCreatedAt())
                    .fromGrade(c.getFromGrade())
                    .toGrade(c.getToGrade())
                    .reason(c.getReason())
                    .actor(c.getActor() != null ? c.getActor().getEmpName() : null)
                    .build());
        }

//        최종 조립
        return EvalGradeDetailDto.builder()
                .empNum(g.getEmp().getEmpNum())
                .empName(g.getEmp().getEmpName())
                .deptName(g.getDeptNameSnapshot())
                .position(g.getPositionSnapshot())
                .seasonName(season.getName())
                .finalGrade(g.getFinalGrade())
                .rankInSeason(g.getRankInSeason())
                .goals(goalDtos)
                .itemScores(itemScores)
                .selfEvalEntries(selfEntries)
                .managerEvalEntry(mgrDto)
                // 가감점 기능 제거 — 2026-04
                // .adjustments(adjDtos)
                .rawScore(g.getTotalScore())
                .teamAvg(g.getTeamAvg())
                .teamStd(g.getTeamStdDev())
                .companyAvg(g.getCompanyAvg())
                .companyStd(g.getCompanyStdDev())
                .adjustedScore(g.getBiasAdjustedScore())
                .autoGrade(g.getAutoGrade())
                .calibrations(calibDtos)
                .lockedAt(g.getLockedAt())
                .build();
    }


    //    16. 본인이 속한 시즌 목록 (드롭다운용, 최신순)
    @Transactional(readOnly = true)
    public List<MySeasonOptionDto> getMySeasons(UUID companyId, Long empId) {
        List<Season> seasons = evalGradeRepository.findSeasonsByCompanyIdAndEmpId(companyId, empId);
        List<MySeasonOptionDto> result = new ArrayList<>();
        for (Season s : seasons) {
            MyResultStatus status = s.getFinalizedAt() != null ? MyResultStatus.FINALIZED : MyResultStatus.IN_PROGRESS;
            result.add(MySeasonOptionDto.builder()
                    .seasonId(s.getSeasonId())
                    .name(s.getName())
                    .status(status)
                    .finalizedAt(s.getFinalizedAt())
                    .startDate(s.getStartDate())
                    .build());
        }
        return result;
    }


    //    17. 본인 평가결과 상세 - 특정 시즌
    //       - Stage 진행 상태 기반으로 일괄 공개 (개인별 공개 X)
    //       - GRADING stage 시작/종료 후 상위자평가 결과 공개
    //       - GRADING stage 종료 후 자동산정 등급 공개
    //       - finalizedAt 있을 때 최종 등급 공개
    @Transactional(readOnly = true)
    public MyEvalResultDto getMyResult(UUID companyId, Long empId, Long seasonId) {

//        EvalGrade 로드 + 회사 소유권 검증
        EvalGrade g = evalGradeRepository.findByEmp_EmpIdAndSeason_SeasonId(empId, seasonId).orElseThrow(() -> new IllegalArgumentException("평가 정보를 찾을 수 없습니다"));
        if (!g.getEmp().getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("접근 권한 없음");
        }
        Season season = g.getSeason();

//        GRADING Stage 찾기 - 공개 타이밍 판단 기준
        List<Stage> stages = stageRepository.findBySeason_SeasonId(seasonId);
        Stage gradingStage = null;
        for (Stage st : stages) {
            if (st.getType() == StageType.GRADING) {
                gradingStage = st;
                break;
            }
        }
//        공개 단계별 2단계, 자동산정 =db조회
//        gradingRevealed : 등급산정 및 보정(GRADING) 시작(IN_PROGRESS 또는 FINISHED) -> 상위자평가 결과/목표별 자기평가 공개
//        finalized : 시즌 최종 확정 -> 최종 등급 공개
//        autoGrade= DB 값 있으면 노출
        boolean gradingRevealed = gradingStage != null && gradingStage.getStatus() != StageStatus.WAITING;
        boolean finalized = season.getFinalizedAt() != null;

//        상위자평가 (등급/피드백) - GRADING 시작 이후 공개
        ManagerEvaluation mgrEval = mgrEvalRepository.findByEmployee_EmpIdAndSeason_SeasonId(empId, seasonId).orElse(null); //팀장평가 1건조회
        String managerGrade = (gradingRevealed && mgrEval != null) ? mgrEval.getGradeLabel() : null;
        String feedback = (gradingRevealed && mgrEval != null) ? mgrEval.getFeedback() : null;

//        승인 목표 + 자기평가 - GRADING 시작 이후 공개
        List<MyEvalResultDto.GoalResult> goalDtos = new ArrayList<>();
        if (gradingRevealed) {
            List<Goal> goals = goalRepository.findByEmp_EmpIdAndSeason_SeasonIdAndApprovalStatusOrderByGoalIdDesc(empId, seasonId, GoalApprovalStatus.APPROVED);
            List<Long> goalIds = new ArrayList<>();
            for (Goal go : goals) goalIds.add(go.getGoalId());

            List<SelfEvaluation> selfEvals = goalIds.isEmpty() ? new ArrayList<>() : selfEvaluationRepository.findByGoal_GoalIdIn(goalIds);
            Map<Long, SelfEvaluation> selfByGoal = new HashMap<>();
            for (SelfEvaluation se : selfEvals) {
                selfByGoal.put(se.getGoal().getGoalId(), se);
            }

            // 시즌 스냅샷에서 KPI cap / maintainTolerance 추출 (없으면 기본 120 / 0)
            BigDecimal kpiCap = BigDecimal.valueOf(120);
            BigDecimal kpiTolerance = BigDecimal.ZERO;
            if (season.getFormSnapshot() != null) {
                try {
                    FormSnapshotDto snapshot = objectMapper.readValue(season.getFormSnapshot(), FormSnapshotDto.class);
                    if (snapshot.getKpiScoring() != null) {
                        if (snapshot.getKpiScoring().getCap() != null) kpiCap = snapshot.getKpiScoring().getCap();
                        if (snapshot.getKpiScoring().getMaintainTolerance() != null) kpiTolerance = snapshot.getKpiScoring().getMaintainTolerance();
                    }
                } catch (JsonProcessingException e) {
                    // 파싱 실패 시 기본값 유지
                }
            }

            for (Goal go : goals) {
                SelfEvaluation se = selfByGoal.get(go.getGoalId());
                BigDecimal actual = se != null ? se.getActualValue() : null;
                BigDecimal rate = null;
                if (go.getGoalType() == GoalType.KPI && actual != null && go.getTargetValue() != null
                        && go.getKpiTemplate() != null) {
                    rate = computeAchievementRate(go.getKpiDirection(),
                                                   go.getTargetValue(), actual, kpiCap, kpiTolerance);
                }
                goalDtos.add(MyEvalResultDto.GoalResult.builder()
                        .goalType(go.getGoalType())
                        .category(go.getCategory())
                        .title(go.getTitle())
                        .weight(go.getWeight())
                        .targetValue(go.getTargetValue())
                        .targetUnit(go.getTargetUnit())
                        .actualValue(actual)
                        .achievementRate(rate)
                        .selfLevel(se != null ? se.getAchievementLevel() : null)
                        .direction(go.getKpiDirection())
                        .build());
            }
        }

//        상태 판단 (enum)
        MyResultStatus status = finalized ? MyResultStatus.FINALIZED : MyResultStatus.IN_PROGRESS;

        return MyEvalResultDto.builder()
                .status(status)
                .finalizedAt(season.getFinalizedAt())
                .autoGrade(g.getAutoGrade()) //예정등급
                .managerGrade(managerGrade)
                .finalGrade(finalized ? g.getFinalGrade() : null) // 최종확정 공개 - finalized=true 일 때만
                .feedback(feedback)
                .goals(goalDtos)
                .build();
    }


    //    18. HR 전체 결과 조회 드롭다운 - 회사 시즌 목록 (최신순, OPEN 이후만 — DRAFT 제외)
    @Transactional(readOnly = true)
    public List<MySeasonOptionDto> getAllSeasons(UUID companyId) {
        List<Season> seasons = seasonRepository.findAllByCompany(companyId);
        List<MySeasonOptionDto> result = new ArrayList<>();
        for (Season s : seasons) {
            if (s.getStatus() == EvalSeasonStatus.DRAFT) continue;
            MyResultStatus status = s.getFinalizedAt() != null ? MyResultStatus.FINALIZED : MyResultStatus.IN_PROGRESS;
            result.add(MySeasonOptionDto.builder()
                    .seasonId(s.getSeasonId())
                    .name(s.getName())
                    .status(status)
                    .finalizedAt(s.getFinalizedAt())
                    .startDate(s.getStartDate())
                    .build());
        }
        return result;
    }


    //    KPI 달성률 계산 (direction 공식 분기)
    //    cap       : 시즌 스냅샷(kpiScoring.cap) 달성률 상한. DOWN+actual=0 의 ÷0 대체값
    //    tolerance : 시즌 스냅샷(kpiScoring.maintainTolerance) MAINTAIN ±n% 이내 만점 처리
    private BigDecimal computeAchievementRate(KpiDirection direction, BigDecimal target, BigDecimal actual,
                                              BigDecimal cap, BigDecimal tolerance) {
        if (direction == null || target == null || target.signum() == 0) return null;
        if (direction == KpiDirection.UP) {
            return actual.multiply(BigDecimal.valueOf(100))
                    .divide(target, 1, RoundingMode.HALF_UP);
        }
        if (direction == KpiDirection.DOWN) {
            // actual=0 은 "완벽 0달성"으로 최고 성과. 역비율이 ÷0 이라 불가능하므로 시즌 cap 으로 치환
            if (actual.signum() == 0) {
                return cap.setScale(1, RoundingMode.HALF_UP);
            }
            return target.multiply(BigDecimal.valueOf(100))
                    .divide(actual, 1, RoundingMode.HALF_UP);
        }
        if (direction == KpiDirection.MAINTAIN) {
            // 편차율(%) = |target - actual| / target × 100
            BigDecimal deviationPct = target.subtract(actual).abs()
                    .divide(target, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            // tolerance 이내면 100% 만점 처리
            BigDecimal tol = tolerance != null ? tolerance : BigDecimal.ZERO;
            if (deviationPct.compareTo(tol) <= 0) {
                return BigDecimal.valueOf(100).setScale(1);
            }
            // tolerance 초과분만큼 감점, 0% 바닥 clamp
            BigDecimal rate = BigDecimal.valueOf(100).subtract(deviationPct.subtract(tol));
            if (rate.signum() < 0) rate = BigDecimal.ZERO;
            return rate.setScale(1, RoundingMode.HALF_UP);
        }
        return null;
    }

}

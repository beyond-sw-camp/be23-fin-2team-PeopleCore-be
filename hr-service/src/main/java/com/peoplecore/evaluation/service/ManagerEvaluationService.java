package com.peoplecore.evaluation.service;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.evaluation.domain.EvalGrade;
import com.peoplecore.evaluation.domain.EvalSeasonStatus;
import com.peoplecore.evaluation.domain.Goal;
import com.peoplecore.evaluation.domain.GoalApprovalStatus;
import com.peoplecore.evaluation.domain.GoalType;
import com.peoplecore.evaluation.domain.ManagerEvaluation;
import com.peoplecore.evaluation.domain.MyResultStatus;
import com.peoplecore.evaluation.domain.Season;
import com.peoplecore.evaluation.domain.SelfEvaluation;
import com.peoplecore.evaluation.domain.Stage;
import com.peoplecore.evaluation.domain.StageStatus;
import com.peoplecore.evaluation.domain.StageType;
import com.peoplecore.evaluation.dto.ManagerEvalAchievementDto;
import com.peoplecore.evaluation.dto.ManagerEvalDetailDto;
import com.peoplecore.evaluation.dto.ManagerEvalRequest;
import com.peoplecore.evaluation.dto.MySeasonOptionDto;
import com.peoplecore.evaluation.dto.TeamMemberEvalListDto;
import com.peoplecore.evaluation.dto.TeamMemberResultDto;
import com.peoplecore.evaluation.repository.EvalGradeRepository;
import com.peoplecore.evaluation.repository.GoalRepository;
import com.peoplecore.evaluation.repository.ManagerEvaluationRepository;
import com.peoplecore.evaluation.repository.SeasonRepository;
import com.peoplecore.evaluation.repository.SelfEvaluationRepository;
import com.peoplecore.evaluation.repository.StageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// 팀장평가 - 팀장이 팀원 실적/등급 평가
@Service
@Transactional
public class ManagerEvaluationService {

    private final ManagerEvaluationRepository mgrEvalRepository;
    private final EmployeeRepository employeeRepository;
    private final SeasonRepository seasonRepository;
    private final GoalRepository goalRepository;
    private final SelfEvaluationRepository selfEvaluationRepository;
    private final EvalGradeRepository evalGradeRepository;
    private final StageRepository stageRepository;

    public ManagerEvaluationService(ManagerEvaluationRepository mgrEvalRepository,
                                    EmployeeRepository employeeRepository,
                                    SeasonRepository seasonRepository,
                                    GoalRepository goalRepository,
                                    SelfEvaluationRepository selfEvaluationRepository,
                                    EvalGradeRepository evalGradeRepository,
                                    StageRepository stageRepository) {
        this.mgrEvalRepository = mgrEvalRepository;
        this.employeeRepository = employeeRepository;
        this.seasonRepository = seasonRepository;
        this.goalRepository = goalRepository;
        this.selfEvaluationRepository = selfEvaluationRepository;
        this.evalGradeRepository = evalGradeRepository;
        this.stageRepository = stageRepository;
    }


    // 1. 팀원 목록 - 이름/부서/직급 + 승인 목표 KPI/OKR 수 + 자기평가/팀장평가 제출 여부
    @Transactional(readOnly = true)
    public List<TeamMemberEvalListDto> getTeamMembers(UUID companyId, Long managerEmpId) {

//        팀장 정보 + 같은 부서 팀원 (본인 제외)
        Employee manager = employeeRepository.findById(managerEmpId).orElse(null);
        if (manager == null || !manager.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("팀장 정보가 없습니다");
        }
        List<Employee> all = employeeRepository.findActiveByCompanyAndDept(companyId, manager.getDept().getDeptId());
        List<Employee> members = new ArrayList<>();
        for (Employee e : all) {
            if (!e.getEmpId().equals(managerEmpId)) members.add(e);
        }
        if (members.isEmpty()) return new ArrayList<>();

//        현재 OPEN 시즌
        Season openSeason = seasonRepository.findByCompany_CompanyIdAndStatus(companyId, EvalSeasonStatus.OPEN).orElse(null);
        if (openSeason == null) {
            throw new IllegalStateException("현재 진행 중인 시즌이 없습니다");
        }

//        팀원 empId 배치 조회용
        List<Long> memberEmpIds = new ArrayList<>();
        for (Employee e : members) memberEmpIds.add(e.getEmpId());

//        팀원 전체 목표 조회 후 승인된 것만 사원별 KPI/OKR 수 집계
        List<Goal> allGoals = goalRepository.findByEmp_EmpIdInAndSeason_SeasonIdOrderByGoalIdDesc(
                memberEmpIds, openSeason.getSeasonId());
        Map<Long, Integer> kpiCountByEmp = new HashMap<>();
        Map<Long, Integer> okrCountByEmp = new HashMap<>();
        List<Long> approvedGoalIds = new ArrayList<>();
        for (Goal g : allGoals) {
            if (g.getApprovalStatus() != GoalApprovalStatus.APPROVED) continue;
            approvedGoalIds.add(g.getGoalId());
            Long eid = g.getEmp().getEmpId();
            if ("KPI".equals(g.getGoalType())) {
                Integer cur = kpiCountByEmp.get(eid);
                kpiCountByEmp.put(eid, cur == null ? 1 : cur + 1);
            } else if ("OKR".equals(g.getGoalType())) {
                Integer cur = okrCountByEmp.get(eid);
                okrCountByEmp.put(eid, cur == null ? 1 : cur + 1);
            }
        }

//        자기평가 제출 여부 - 승인 목표 중 submittedAt != null 인 SelfEval 의 소유 사원 집합
        Set<Long> selfSubmittedEmpIds = new HashSet<>();
        if (!approvedGoalIds.isEmpty()) {
            List<SelfEvaluation> selfEvals = selfEvaluationRepository.findByGoal_GoalIdIn(approvedGoalIds);
            for (SelfEvaluation se : selfEvals) {
                if (se.getSubmittedAt() != null) {
                    selfSubmittedEmpIds.add(se.getGoal().getEmp().getEmpId());
                }
            }
        }

//        팀장 본인이 제출 완료한 팀장평가 대상 사원 집합
        List<ManagerEvaluation> mgrEvals = mgrEvalRepository.findByEvaluator_EmpIdAndSeason_SeasonId(
                managerEmpId, openSeason.getSeasonId());
        Set<Long> mgrSubmittedEmpIds = new HashSet<>();
        for (ManagerEvaluation me : mgrEvals) {
            if (me.getSubmittedAt() != null) {
                mgrSubmittedEmpIds.add(me.getEmployee().getEmpId());
            }
        }

//        응답 조립 - 팀원 순서 유지
        List<TeamMemberEvalListDto> result = new ArrayList<>();
        for (Employee e : members) {
            Long eid = e.getEmpId();
            Integer kpi = kpiCountByEmp.get(eid);
            Integer okr = okrCountByEmp.get(eid);
            result.add(TeamMemberEvalListDto.builder()
                    .empId(eid)
                    .name(e.getEmpName())
                    .dept(e.getDept() != null ? e.getDept().getDeptName() : null)
                    .position(e.getGrade() != null ? e.getGrade().getGradeName() : null)
                    .kpiCount(kpi == null ? 0 : kpi)
                    .okrCount(okr == null ? 0 : okr)
                    .selfEvalSubmitted(selfSubmittedEmpIds.contains(eid))
                    .managerEvalSubmitted(mgrSubmittedEmpIds.contains(eid))
                    .build());
        }
        return result;
    }


    // 2. 팀원 달성도 조회 (플로팅 패널용) - 승인된 KPI/OKR + 자기평가 실적값
    @Transactional(readOnly = true)
    public ManagerEvalAchievementDto getAchievement(UUID companyId, Long managerEmpId, Long empId) {

//        팀장이 이 사원을 관리하는 팀원 범위 확인
        validateTeamMember(companyId, managerEmpId, empId);

//        현재 OPEN 시즌
        Season openSeason = seasonRepository.findByCompany_CompanyIdAndStatus(companyId, EvalSeasonStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("현재 진행 중인 시즌이 없습니다"));

//        대상 사원의 승인된 목표 조회
        List<Goal> goals = goalRepository.findByEmp_EmpIdAndSeason_SeasonIdAndApprovalStatusOrderByGoalIdDesc(
                empId, openSeason.getSeasonId(), GoalApprovalStatus.APPROVED);
        if (goals.isEmpty()) {
            return ManagerEvalAchievementDto.builder()
                    .kpiList(new ArrayList<>())
                    .okrList(new ArrayList<>())
                    .build();
        }

//        해당 목표들의 자기평가 배치 조회 - actualValue / achievementLevel 추출용
        List<Long> goalIds = new ArrayList<>();
        for (Goal g : goals) goalIds.add(g.getGoalId());
        List<SelfEvaluation> selfEvals = selfEvaluationRepository.findByGoal_GoalIdIn(goalIds);
        Map<Long, SelfEvaluation> selfByGoalId = new HashMap<>();
        for (SelfEvaluation se : selfEvals) {
            selfByGoalId.put(se.getGoal().getGoalId(), se);
        }

//        KPI / OKR 분리 DTO 조립
        List<ManagerEvalAchievementDto.KpiItem> kpiList = new ArrayList<>();
        List<ManagerEvalAchievementDto.OkrItem> okrList = new ArrayList<>();
        for (Goal g : goals) {
            SelfEvaluation se = selfByGoalId.get(g.getGoalId());
            if (g.getGoalType() == GoalType.KPI) {
                kpiList.add(ManagerEvalAchievementDto.KpiItem.builder()
                        .category(g.getCategory())
                        .title(g.getTitle())
                        .targetValue(g.getTargetValue())
                        .targetUnit(g.getTargetUnit())
                        .actualValue(se != null ? se.getActualValue() : null)
                        .direction(g.getKpiDirection())
                        .build());
            } else if (g.getGoalType() == GoalType.OKR) {
                String selfLevel = null;
                if (se != null && se.getAchievementLevel() != null) {
                    selfLevel = se.getAchievementLevel().name();
                }
                okrList.add(ManagerEvalAchievementDto.OkrItem.builder()
                        .category(g.getCategory())
                        .title(g.getTitle())
                        .selfLevel(selfLevel)
                        .build());
            }
        }

        return ManagerEvalAchievementDto.builder()
                .kpiList(kpiList)
                .okrList(okrList)
                .build();
    }


    // 3. 기존 팀장평가 조회 (임시저장 복구/수정용) - 없으면 빈 DTO
    @Transactional(readOnly = true)
    public ManagerEvalDetailDto getEvaluation(UUID companyId, Long managerEmpId, Long empId) {

//        팀원 범위 확인
        validateTeamMember(companyId, managerEmpId, empId);

//        현재 OPEN 시즌
        Season openSeason = seasonRepository.findByCompany_CompanyIdAndStatus(companyId, EvalSeasonStatus.OPEN).orElseThrow(() -> new IllegalStateException("현재 진행 중인 시즌이 없습니다"));

//        기존 평가 조회 - 없으면 빈 DTO (프론트에서 "새로 작성" UX)
        ManagerEvaluation me = mgrEvalRepository.findByEmployee_EmpIdAndEvaluator_EmpIdAndSeason_SeasonId(empId, managerEmpId, openSeason.getSeasonId()).orElse(null);
        if (me == null) {
            return ManagerEvalDetailDto.builder().build();
        }

        return ManagerEvalDetailDto.builder()
                .grade(me.getGradeLabel())
                .comment(me.getComment())
                .feedback(me.getFeedback())
                .submittedAt(me.getSubmittedAt())
                .build();
    }


    // 4. 임시 저장 - 기존 평가 있으면 update, 없으면 insert (upsert)
    //   미평가/제출 상태 유지(submittedAt X)
    public void saveDraft(UUID companyId, Long managerEmpId, Long empId, ManagerEvalRequest request) {

//        팀원 범위 확인
        validateTeamMember(companyId, managerEmpId, empId);

//        현재 OPEN 시즌
        Season openSeason = seasonRepository.findByCompany_CompanyIdAndStatus(companyId, EvalSeasonStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException("현재 진행 중인 시즌이 없습니다"));

//        기존 평가 조회 - 있으면 update, 없으면 새로 생성
        ManagerEvaluation existing = mgrEvalRepository.findByEmployee_EmpIdAndEvaluator_EmpIdAndSeason_SeasonId(empId, managerEmpId, openSeason.getSeasonId()).orElse(null);

        if (existing != null) {
//            dirty checking 으로 자동 UPDATE
            existing.saveDraft(request.getGrade(), request.getComment(), request.getFeedback());
            return;
        }

//        신규 row 생성 - evaluator/employee 엔티티 로드 필요
        Employee evaluator = employeeRepository.findById(managerEmpId).orElseThrow(() -> new IllegalArgumentException("팀장 정보가 없습니다"));
        Employee employee = employeeRepository.findById(empId).orElseThrow(() -> new IllegalArgumentException("팀원 정보가 없습니다"));

        ManagerEvaluation newEval = ManagerEvaluation.builder()
                .evaluator(evaluator)
                .employee(employee)
                .season(openSeason)
                .gradeLabel(request.getGrade())
                .comment(request.getComment())
                .feedback(request.getFeedback())
                .build();
        mgrEvalRepository.save(newEval);
    }


    // 5. 최종 제출 - 기존 평가 O= update+submit, X= insert+submit (submittedAt 기록)
    public void submit(UUID companyId, Long managerEmpId, Long empId, ManagerEvalRequest request) {

//        팀원 범위 확인
        validateTeamMember(companyId, managerEmpId, empId);

//        현재 OPEN 시즌
        Season openSeason = seasonRepository.findByCompany_CompanyIdAndStatus(companyId, EvalSeasonStatus.OPEN).orElseThrow(() -> new IllegalStateException("현재 진행 중인 시즌이 없습니다"));

//        기존 평가 조회 - 있으면 submit (dirty checking 으로 UPDATE)
        ManagerEvaluation existing = mgrEvalRepository.findByEmployee_EmpIdAndEvaluator_EmpIdAndSeason_SeasonId(empId, managerEmpId, openSeason.getSeasonId()).orElse(null);

        if (existing != null) {
            existing.submit(request.getGrade(), request.getComment(), request.getFeedback());
            return;
        }

//        신규 row 생성 + 바로 제출 처리
        Employee evaluator = employeeRepository.findById(managerEmpId)
                .orElseThrow(() -> new IllegalArgumentException("팀장 정보가 없습니다"));
        Employee employee = employeeRepository.findById(empId)
                .orElseThrow(() -> new IllegalArgumentException("팀원 정보가 없습니다"));

        ManagerEvaluation newEval = ManagerEvaluation.builder()
                .evaluator(evaluator)
                .employee(employee)
                .season(openSeason)
                .build();
        newEval.submit(request.getGrade(), request.getComment(), request.getFeedback());
        mgrEvalRepository.save(newEval);
    }


    // 6. 팀원 최종 평가결과 일괄 조회 - 팀장 기준, 특정 시즌 (과거 포함)
    //   - GRADING 단계 시작 이후 결과공개
    //   - gradeFilter: null=전체 / 값(S/A/B/C/D 등) = 최종등급 일치만

    @Transactional(readOnly = true)
    public List<TeamMemberResultDto> getTeamResults(UUID companyId, Long managerEmpId, Long seasonId, String gradeFilter) {

//        팀장 회사 소유권 검증
        Employee manager = employeeRepository.findById(managerEmpId).orElse(null);
        if (manager == null || !manager.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("팀장 정보가 없습니다");
        }

//        시즌 회사 소유권 검증
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new IllegalArgumentException("시즌 정보가 없습니다"));
        if (!season.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("접근 권한 없음");
        }

//        GRADING 시작 전이면 빈 리스트 ("결과공개기간 아닙니다" 표시)
        if (!isGradingRevealed(seasonId)) return new ArrayList<>();

//        finalGrade 는 시즌 확정 후에만 공개 (autoGrade / managerGrade 는 조건부 자동 노출)
        boolean finalized = season.getFinalizedAt() != null;

//        해당 시즌 팀장이 평가한 팀원 목록 (그 시즌의 팀 소속 박제)
        List<ManagerEvaluation> mgrEvals = mgrEvalRepository.findByEvaluator_EmpIdAndSeason_SeasonId(managerEmpId, seasonId);
        if (mgrEvals.isEmpty()) return new ArrayList<>();

//        팀원 empId 목록 -> EvalGrade 배치 조회 후 Map (N+1 방지)
        Set<Long> empIds = new HashSet<>();
        for (ManagerEvaluation me : mgrEvals) empIds.add(me.getEmployee().getEmpId());
        List<EvalGrade> grades = evalGradeRepository.findBySeason_SeasonId(seasonId);
        Map<Long, EvalGrade> gradeByEmp = new HashMap<>();
        for (EvalGrade g : grades) {
            if (empIds.contains(g.getEmp().getEmpId())) {
                gradeByEmp.put(g.getEmp().getEmpId(), g);
            }
        }

//        DTO 조립 (finalGrade 필터는 finalized 이후에만 유의미)
        List<TeamMemberResultDto> result = new ArrayList<>();
        for (ManagerEvaluation me : mgrEvals) {
            Employee emp = me.getEmployee();
            EvalGrade g = gradeByEmp.get(emp.getEmpId());
            String finalGrade = finalized && g != null ? g.getFinalGrade() : null;

//            gradeFilter 지정 시 최종등급 일치만 포함 (대소문자 무시)
            if (gradeFilter != null && !gradeFilter.isBlank()
                    && (finalGrade == null || !finalGrade.equalsIgnoreCase(gradeFilter))) {
                continue;
            }

            result.add(TeamMemberResultDto.builder()
                    .empId(emp.getEmpId())
                    .empName(emp.getEmpName())
                    .position(g != null ? g.getPositionSnapshot() : null)
                    .managerGradeId(me.getGradeLabel())
                    .autoGradeId(g != null ? g.getAutoGrade() : null)
                    .finalGradeId(finalGrade)
                    .managerComment(me.getComment())
                    .managerFeedback(me.getFeedback())
                    .build());
        }
        return result;
    }


    // 7. 팀장 평가 결과 드롭다운 - 팀장이 평가자로 참여한 시즌 목록 (최신순, 과거 포함)
    @Transactional(readOnly = true)
    public List<MySeasonOptionDto> getTeamResultSeasons(UUID companyId, Long managerEmpId) {
        List<Season> seasons = mgrEvalRepository.findSeasonsByCompanyIdAndEvaluator(companyId, managerEmpId);
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


    // GRADING 단계 시작(IN_PROGRESS 또는 FINISHED) 여부 - MyResult와 동일
    private boolean isGradingRevealed(Long seasonId) {
        List<Stage> stages = stageRepository.findBySeason_SeasonId(seasonId);
        for (Stage st : stages) {
            if (st.getType() == StageType.GRADING) {
                return st.getStatus() != StageStatus.WAITING;
            }
        }
        return false;
    }


    // 팀장 기준 팀원 범위 확인 (같은 부서, 본인 제외)
    private void validateTeamMember(UUID companyId, Long managerEmpId, Long targetEmpId) {
        Employee manager = employeeRepository.findById(managerEmpId).orElse(null);
        if (manager == null || !manager.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("팀장 정보가 없습니다");
        }
        Employee target = employeeRepository.findById(targetEmpId).orElse(null);
        if (target == null || !target.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("팀원 정보가 없습니다");
        }
        if (target.getDept() == null || manager.getDept() == null || !target.getDept().getDeptId().equals(manager.getDept().getDeptId()) || target.getEmpId().equals(managerEmpId)) {
            throw new IllegalArgumentException("본인 팀원이 아닙니다");
        }
    }
}

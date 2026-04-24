package com.peoplecore.evaluation.service;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.evaluation.domain.*;
import com.peoplecore.evaluation.dto.GoalDeleteResultDto;
import com.peoplecore.evaluation.dto.GoalRequest;
import com.peoplecore.evaluation.dto.GoalResponse;
import com.peoplecore.evaluation.dto.TeamMemberGoalResponse;
import com.peoplecore.evaluation.repository.GoalRepository;
import com.peoplecore.evaluation.repository.KpiTemplateRepository;
import com.peoplecore.evaluation.repository.SeasonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 목표 - 사원 목표 등록/수정 및 팀장 승인/반려
@Service
@Transactional
public class GoalService {

    private final GoalRepository goalRepository;
    private final SeasonRepository seasonRepository;
    private final EmployeeRepository employeeRepository;
    private final KpiTemplateRepository kpiTemplateRepository;
    private final EvaluationRulesService rulesService;

    public GoalService(GoalRepository goalRepository,
                       SeasonRepository seasonRepository, EmployeeRepository employeeRepository,
                       KpiTemplateRepository kpiTemplateRepository, EvaluationRulesService rulesService) {
        this.goalRepository = goalRepository;
        this.seasonRepository = seasonRepository;
        this.employeeRepository = employeeRepository;
        this.kpiTemplateRepository = kpiTemplateRepository;
        this.rulesService = rulesService;
    }

    // 1번 본인 목표 목록 조회 - 회사의 OPEN 시즌만
    @Transactional(readOnly = true)
    public List<GoalResponse> getMyGoals(UUID companyId, Long empId) {

        // 현재 진행 시즌 (회사당 1개 보장)
        Season openSeason = seasonRepository.findByCompany_CompanyIdAndStatus(companyId, EvalSeasonStatus.OPEN).orElse(null);
        if (openSeason == null) {
            throw new IllegalStateException("현재 진행 중인 시즌이 없습니다");
        }

        // 본인 + 해당 시즌 목표 조회
        List<Goal> goals = goalRepository.findByEmp_EmpIdAndSeason_SeasonIdOrderByGoalIdDesc(empId, openSeason.getSeasonId());

        // 회사 규칙 로드 -> 승인된 목표 비율 계산
        EvaluationRules rules = rulesService.getEntityByCompanyId(companyId);
        Map<Long, BigDecimal> ratios = computeRatios(goals, rules);

        // Entity -> DTO 변환 (비율 주입)
        List<GoalResponse> result = new ArrayList<>();
        for (Goal g : goals) {
            GoalResponse dto = GoalResponse.from(g);
            dto.setRatio(ratios.get(g.getGoalId()));
            result.add(dto);
        }
        return result;
    }

    //    2번 신규등록 -kpi=템플릿, okr=요청값
    public GoalResponse createGoal(UUID companyId, Long empId, GoalRequest request) {

//        현재 오픈 시즌
        Season openSeason = seasonRepository.findByCompany_CompanyIdAndStatus(companyId, EvalSeasonStatus.OPEN).orElse(null);
        if (openSeason == null) {
            throw new IllegalStateException("현재 진행 중인 시즌이 없습니다");
        }

//        사원, 회사 검증
        Employee employee = employeeRepository.findById(empId).orElse(null);
        if (employee == null) {
            throw new IllegalArgumentException("사원을 찾을 수 없습니다");
        }
        if (!employee.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("다른 회사 사원입니다");
        }
//        껍데기 엔티티 생성 후 헬퍼로 타입별 필드 주입 -- TODO 동적 디벨롭
        Goal goal = Goal.builder()
                .emp(employee)
                .season(openSeason)
                .approvalStatus(GoalApprovalStatus.DRAFT)
                .build();
        applyRequestToGoal(goal, companyId, request, request.getGrade());

        Goal saved = goalRepository.save(goal);
        return GoalResponse.from(saved);
    }

    //    3번 수정 - 본인 소유 + 작성중/반려 상태만. 반려였으면 작성중/대기로 리셋
    public GoalResponse updateGoal(UUID companyId, Long empId, Long goalId, GoalRequest request) {

//        기존 목표 로드
        Goal goal = goalRepository.findById(goalId).orElse(null);
        if (goal == null) {
            throw new IllegalArgumentException("목표를 찾을 수 없습니다");
        }

//        본인 소유 검증
        if (!goal.getEmp().getEmpId().equals(empId)) {
            throw new IllegalArgumentException("본인 목표만 수정할 수 있습니다");
        }

//        수정 가능 상태 검증 (작성중 OR 반려만)
        GoalApprovalStatus status = goal.getApprovalStatus();
        if (status != GoalApprovalStatus.DRAFT && status != GoalApprovalStatus.REJECTED) {
            throw new IllegalStateException("제출완료/승인된 목표는 수정할 수 없습니다");
        }

        applyRequestToGoal(goal, companyId, request, request.getGrade());

//        반려 상태 유지 - rejectReason 도 그대로 남겨서 사원이 재작성 근거로 참조
//        제출(submit) 호출 시에만 PENDING 전환 + rejectReason 초기화
        // dirty checking 으로 자동 UPDATE
        return GoalResponse.from(goal);
    }

    //    4번 삭제 - 본인 소유 + 작성중/반려 상태만 삭제 가능
    //      - KPI 삭제 시 cascade 체크: 마지막 KPI + OKR 존재 → confirm 확인 후 OKR 일괄 삭제
    //      - confirm=false 로 들어오면 DB 변경 없이 cascade 대상 목록을 담아 반환
    //      - OKR 중 PENDING/APPROVED 있으면 cascade 불가 → throw (OKR 먼저 처리 유도)
    public GoalDeleteResultDto deleteGoal(UUID companyId, Long empId, Long goalId, boolean confirm) {

//        기존 목표 로드
        Goal goal = goalRepository.findById(goalId).orElse(null);
        if (goal == null) {
            throw new IllegalArgumentException("목표를 찾을 수 없습니다");
        }

//        본인 소유 검증
        if (!goal.getEmp().getEmpId().equals(empId)) {
            throw new IllegalArgumentException("본인 목표만 삭제할 수 있습니다");
        }

//        회사 일치 검증 (이중 방어)
        if (!goal.getEmp().getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("다른 회사 목표는 삭제할 수 없습니다");
        }

//        삭제 가능 상태 검증 (작성중 OR 반려만 허용)
        GoalApprovalStatus status = goal.getApprovalStatus();
        if (status != GoalApprovalStatus.DRAFT && status != GoalApprovalStatus.REJECTED) {
            throw new IllegalStateException("제출완료/승인된 목표는 삭제할 수 없습니다");
        }

//        KPI cascade 체크
//          - 삭제 대상이 KPI 이고 다른 KPI 가 없고 OKR 이 있으면 "마지막 KPI + OKR 잔존" 상황
//          - confirm=false 면 삭제하지 말고 cascade 대상 목록 반환 (프론트 다이얼로그용)
//          - confirm=true 면 OKR 전부 + 해당 KPI 일괄 삭제
        if (goal.getGoalType() == GoalType.KPI) {
            Long seasonId = goal.getSeason().getSeasonId();
            List<Goal> allGoals = goalRepository.findByEmp_EmpIdAndSeason_SeasonIdOrderByGoalIdDesc(empId, seasonId);

            boolean hasOtherKpi = false;
            List<Goal> okrs = new ArrayList<>();
            for (Goal g : allGoals) {
                if (g.getGoalId().equals(goalId)) continue;   // 자기 자신 제외
                if (g.getGoalType() == GoalType.KPI) { hasOtherKpi = true; }
                else if (g.getGoalType() == GoalType.OKR) { okrs.add(g); }
            }

            if (!hasOtherKpi && !okrs.isEmpty()) {
//                cascade 가능 상태 검증 - OKR 전부 DRAFT/REJECTED 여야 일괄 삭제 가능
                for (Goal okr : okrs) {
                    GoalApprovalStatus okrStatus = okr.getApprovalStatus();
                    if (okrStatus != GoalApprovalStatus.DRAFT && okrStatus != GoalApprovalStatus.REJECTED) {
                        throw new IllegalStateException("제출완료/승인된 OKR 이 있어 KPI 를 삭제할 수 없습니다. OKR 을 먼저 처리하세요.");
                    }
                }

                if (!confirm) {
//                    DB 변경 없이 cascade 대상 목록만 반환 -> 프론트가 다이얼로그 띄우고 confirm=true 로 재호출
                    List<GoalDeleteResultDto.CascadedGoal> cascaded = new ArrayList<>();
                    for (Goal okr : okrs) {
                        cascaded.add(GoalDeleteResultDto.CascadedGoal.builder()
                                .goalId(okr.getGoalId())
                                .title(okr.getTitle())
                                .build());
                    }
                    return GoalDeleteResultDto.builder()
                            .success(false)
                            .requiresConfirm(true)
                            .cascadedOkrs(cascaded)
                            .build();
                }

//                confirm=true -> OKR 일괄 삭제 (KPI 삭제는 아래 공용 경로에서 수행)
                goalRepository.deleteAll(okrs);
            }
        }

        goalRepository.delete(goal);

        return GoalDeleteResultDto.builder()
                .success(true)
                .requiresConfirm(false)
                .cascadedOkrs(Collections.emptyList())
                .build();
    }

    //    5번 단건 제출 - 작성중/반려 상태만 제출 가능
    //  - 프론트 카드별 "제출" 버튼 제거됨. 일괄 제출(submitAllDrafts)로 통합
    //  - 단건 제출 필요 시 아래 주석 해제
    /*
    public GoalResponse submitGoal(UUID companyId, Long empId, Long goalId) {

//        기존 목표 로드
        Goal goal = goalRepository.findById(goalId).orElse(null);
        if (goal == null) {
            throw new IllegalArgumentException("목표를 찾을 수 없습니다");
        }

//        본인 소유 검증
        if (!goal.getEmp().getEmpId().equals(empId)) {
            throw new IllegalArgumentException("본인 목표만 제출할 수 있습니다");
        }

//        회사 일치 검증
        if (!goal.getEmp().getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("다른 회사 목표는 제출할 수 없습니다");
        }

//        제출 가능 상태 검증 (작성중 OR 반려만)
        boolean isDraft = goal.getSubmittedAt() == null;
        boolean isRejected = goal.getApprovalStatus() == GoalApprovalStatus.REJECTED;
        if (!isDraft && !isRejected) {
            throw new IllegalStateException("이미 제출된 목표입니다");
        }

        goal.submit();
        return GoalResponse.from(goal);
    }
    */

    //    6번 일괄 제출 - 본인의 작성중 목표 전체를 제출완료로 전환
    //    (프론트 "작성중 전체 제출" 버튼 대응)
    public List<GoalResponse> submitAllDrafts(UUID companyId, Long empId) {

//        현재 OPEN 시즌
        Season openSeason = seasonRepository.findByCompany_CompanyIdAndStatus(companyId, EvalSeasonStatus.OPEN).orElse(null);
        if (openSeason == null) {
            throw new IllegalStateException("현재 진행 중인 시즌이 없습니다");
        }

//        본인 + 해당 시즌 목표 전체 로드
        List<Goal> goals = goalRepository.findByEmp_EmpIdAndSeason_SeasonIdOrderByGoalIdDesc(empId, openSeason.getSeasonId());

//        회사 규칙 로드 -> 승인된 목표 비율 계산
        EvaluationRules rules = rulesService.getEntityByCompanyId(companyId);
        Map<Long, BigDecimal> ratios = computeRatios(goals, rules);

//        작성중/반려 상태만 submit (대기/승인은 스킵)
        List<GoalResponse> result = new ArrayList<>();
        for (Goal g : goals) {
            // 회사 일치 검증 (이중 방어)
            if (!g.getEmp().getCompany().getCompanyId().equals(companyId)) {
                continue;
            }
            GoalApprovalStatus st = g.getApprovalStatus();
            if (st == GoalApprovalStatus.DRAFT || st == GoalApprovalStatus.REJECTED) {
                g.submit();
            }
            GoalResponse dto = GoalResponse.from(g);
            dto.setRatio(ratios.get(g.getGoalId()));
            result.add(dto);
        }
        return result;
    }

    // 7번 팀원 목표 전체 조회
    //   - 같은 부서 사원= 팀원으로 간주 (권한 정책 임시 - custom 만든 후 findTeamMembers )
    //   - 상단 카드 집계->프론트
    @Transactional(readOnly = true)
    public List<TeamMemberGoalResponse> getTeamGoals(UUID companyId, Long managerId) {

        // 팀원 범위 판단
        List<Employee> teamMembers = findTeamMembers(companyId, managerId);
        if (teamMembers.isEmpty()) return new ArrayList<>();

        // 현재 OPEN 시즌
        Season openSeason = seasonRepository.findByCompany_CompanyIdAndStatus(companyId, EvalSeasonStatus.OPEN).orElse(null);
        if (openSeason == null) {
            throw new IllegalStateException("현재 진행 중인 시즌이 없습니다");
        }

        // 팀원 ID 리스트
        List<Long> teamEmpIds = new ArrayList<>();
        for (Employee e : teamMembers) {
            teamEmpIds.add(e.getEmpId());
        }

        // 팀원들의 목표 일괄 조회 (1 query, N+1 방지) - 팀원 내 목표들 집합
        List<Goal> allGoals = goalRepository.findByEmp_EmpIdInAndSeason_SeasonIdOrderByGoalIdDesc(teamEmpIds, openSeason.getSeasonId());

        // 플랫 리스트를 empId 기준으로 그룹핑 (GROUP BY 대체 - 팀원별 O(1) 조회)
        Map<Long, List<Goal>> goalsByEmpId = new HashMap<>();
        for (Goal g : allGoals) {
            Long eid = g.getEmp().getEmpId();
            List<Goal> list = goalsByEmpId.get(eid);
            if (list == null) {                         // 해당 empId 처음 등장 -> 빈 리스트 생성 후 등록
                list = new ArrayList<>();
                goalsByEmpId.put(eid, list);
            }
            list.add(g);                                // 이 사원의 리스트에 Goal 추가
        }

        // 팀원 순서대로 응답 조립 (사원정보 + 목표 + 가장 늦은 submittedAt)
        List<TeamMemberGoalResponse> result = new ArrayList<>();
        for (Employee emp : teamMembers) {

            List<Goal> memberGoals = goalsByEmpId.get(emp.getEmpId());
            if (memberGoals == null) memberGoals = new ArrayList<>();

            // 가장 늦은 submittedAt 찾으면서 DTO 변환까지 한 번에
            // DRAFT(작성중/미제출) 은 평가자 화면에 노출하지 않음 - PENDING/APPROVED/REJECTED 만
            LocalDateTime latestSubmitted = null;
            List<GoalResponse> goalDtos = new ArrayList<>();
            for (Goal g : memberGoals) {
                if (g.getApprovalStatus() == GoalApprovalStatus.DRAFT) continue;
                goalDtos.add(GoalResponse.from(g));
                if (g.getSubmittedAt() != null && (latestSubmitted == null || g.getSubmittedAt().isAfter(latestSubmitted))) {
                    latestSubmitted = g.getSubmittedAt();
                }
            }

            result.add(TeamMemberGoalResponse.builder()
                    .id(emp.getEmpId())
                    .employeeName(emp.getEmpName())
                    .dept(emp.getDept().getDeptName())
                    .position(emp.getGrade() != null ? emp.getGrade().getGradeName() : null)
                    .submittedDate(latestSubmitted)
                    .goals(goalDtos)
                    .build());
        }
        return result;
    }

    // 8번 단건 승인 - 대기 상태만 가능 (PENDING 이 곧 "제출완료+대기")
    public GoalResponse approveGoal(UUID companyId, Long managerId, Long goalId) {
        Goal goal = loadGoalForManager(companyId, managerId, goalId);

        if (goal.getApprovalStatus() != GoalApprovalStatus.PENDING) {
            throw new IllegalStateException("대기 상태가 아닌 목표는 승인할 수 없습니다");
        }

        goal.approve();
        return GoalResponse.from(goal);
    }

    // 9번 단건 반려 - 대기 상태만 가능, 사유 필수
    public GoalResponse rejectGoal(UUID companyId, Long managerId, Long goalId, String rejectReason) {
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new IllegalArgumentException("반려 사유는 필수입니다");
        }

        Goal goal = loadGoalForManager(companyId, managerId, goalId);

        if (goal.getApprovalStatus() != GoalApprovalStatus.PENDING) {
            throw new IllegalStateException("대기 상태가 아닌 목표는 반려할 수 없습니다");
        }

        goal.reject(rejectReason);
        return GoalResponse.from(goal);
    }

    // 10번 팀원 단위 일괄 승인 - 해당 팀원의 대기+제출완료 목표 전부 승인
    public List<GoalResponse> approveAllPending(UUID companyId, Long managerId, Long targetEmpId) {

        // 팀장 권한 체크
        validateManagerAccess(companyId, managerId, targetEmpId);

        // 현재 OPEN 시즌
        Season openSeason = seasonRepository.findByCompany_CompanyIdAndStatus(companyId, EvalSeasonStatus.OPEN).orElse(null);
        if (openSeason == null) {
            throw new IllegalStateException("현재 진행 중인 시즌이 없습니다");
        }

        // 해당 팀원의 시즌 목표 전체 로드
        List<Goal> goals = goalRepository.findByEmp_EmpIdAndSeason_SeasonIdOrderByGoalIdDesc(targetEmpId, openSeason.getSeasonId());

        // 대기 + 제출완료 상태만 골라 승인
        List<GoalResponse> result = new ArrayList<>();
        for (Goal g : goals) {
            if (g.getApprovalStatus() == GoalApprovalStatus.PENDING) {
                g.approve();
                result.add(GoalResponse.from(g));
            }
        }
        return result;
    }

    // ── 팀장 단건 처리 공통 헬퍼 ──────────────────────

    // Goal 로드 + 팀장 권한 검증 (승인/반려 공통)
    private Goal loadGoalForManager(UUID companyId, Long managerId, Long goalId) {
        Goal goal = goalRepository.findById(goalId).orElse(null);
        if (goal == null) {
            throw new IllegalArgumentException("목표를 찾을 수 없습니다");
        }
        validateManagerAccess(companyId, managerId, goal.getEmp().getEmpId());
        return goal;
    }

    //  단일 사원 접근 권한 체크 (findTeamMembers 기반)
    // TODO: Title 기반 MANAGER role 확정 시 findTeamMembers 만 교체. 해당 메서드는 그대로
    private void validateManagerAccess(UUID companyId, Long managerId, Long targetEmpId) {
        List<Employee> members = findTeamMembers(companyId, managerId);
        for (Employee m : members) {
            if (m.getEmpId().equals(targetEmpId)) return;
        }
        throw new IllegalArgumentException("본인 팀원이 아닙니다");
    }


    // 팀장의 팀원 범위 반환 (권한 정책 임시 - 같은 부서 = 팀원)
    // TODO: Title 기반 MANAGER role 확정 시 메서드 본체 교체
    private List<Employee> findTeamMembers(UUID companyId, Long managerId) {

        Employee manager = employeeRepository.findById(managerId).orElse(null);
        if (manager == null || !manager.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("팀장 정보가 없습니다");
        }

        // 같은 부서 활성 사원 중 본인 제외
        List<Employee> all = employeeRepository.findActiveByCompanyAndDept(companyId, manager.getDept().getDeptId());
        List<Employee> members = new ArrayList<>();
        for (Employee e : all) {
            if (!e.getEmpId().equals(managerId)) members.add(e);
        }
        return members;
    }


    // 승인된 KPI 목표 중 taskWeight 합 기준 백분율 계산
    //   OKR 및 미승인 목표는 결과 Map 에 키 없음 -> DTO 에서 null
    private Map<Long, BigDecimal> computeRatios(List<Goal> goals, EvaluationRules rules) {
        // 승인된 KPI 목표만 수집 (OKR 은 비율 산정 대상 아님)
        List<Goal> approved = new ArrayList<>();
        for (Goal g : goals) {
            if (g.getApprovalStatus() == GoalApprovalStatus.APPROVED
                    && g.getGoalType() == GoalType.KPI) {
                approved.add(g);
            }
        }

        // 전체 가중치 합계
        int total = 0;
        for (Goal g : approved) {
            total += weightOf(g.getTaskGrade(), rules);
        }

        Map<Long, BigDecimal> result = new HashMap<>();
        if (total == 0) return result;

        // 각 목표별 비율
        for (Goal g : approved) {
            int w = weightOf(g.getTaskGrade(), rules);
            BigDecimal ratio = BigDecimal.valueOf(w)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
            result.put(g.getGoalId(), ratio);
        }
        return result;
    }

    // 업무등급별 가중치 조회 (회사 규칙 기반)
    private int weightOf(TaskGrade grade, EvaluationRules rules) {
        if (grade == TaskGrade.HIGH) return rules.getTaskWeightSang();
        if (grade == TaskGrade.MID)  return rules.getTaskWeightJung();
        if (grade == TaskGrade.LOW)  return rules.getTaskWeightHa();
        return 0;
    }


    // 타입별 검증 + Goal 엔티티에 필드 주입 (create/update 공용)
    private void applyRequestToGoal(Goal goal, UUID companyId, GoalRequest request, TaskGrade grade) {

        if (request.getGoalType() == GoalType.KPI) {
            if (request.getKpiTemplateId() == null || request.getTargetValue() == null) {
                throw new IllegalArgumentException("KPI 목표는 지표와 목표값이 필수입니다");
            }
            KpiTemplate t = kpiTemplateRepository.findById(request.getKpiTemplateId()).orElse(null);
            if (t == null) {
                throw new IllegalArgumentException("KPI 지표를 찾을 수 없습니다");
            }
            if (!t.getDepartment().getCompany().getCompanyId().equals(companyId)) {
                throw new IllegalArgumentException("다른 회사 지표는 사용할 수 없습니다");
            }
            goal.updateAsKpi(t, request.getTargetValue(), grade);

        } else if (request.getGoalType() == GoalType.OKR) {
            if (request.getCategory() == null || request.getCategory().isBlank()
                    || request.getTitle() == null || request.getTitle().isBlank()
                    || request.getDescription() == null || request.getDescription().isBlank()) {
                throw new IllegalArgumentException("OKR 목표는 구분과 제목, 설명이 필수입니다");
            }
//            OKR 등록/수정 시 해당 시즌에 본인의 KPI 목표가 1개 이상 있어야 허용
//              - Phase 2 자동산정은 KPI 기반만 집계 → OKR 만 있는 사원은 self 점수 = null 로 missing 처리
//              - 입구에서 "OKR 단독" 상태 자체를 못 만들게 차단
//              - 수정 시에는 자기 자신 제외하고 KPI 존재 여부 판정
            Long currentGoalId = goal.getGoalId();
            List<Goal> existing = goalRepository.findByEmp_EmpIdAndSeason_SeasonIdOrderByGoalIdDesc(
                    goal.getEmp().getEmpId(), goal.getSeason().getSeasonId());
            boolean hasKpi = false;
            for (Goal g : existing) {
                if (currentGoalId != null && g.getGoalId().equals(currentGoalId)) continue;
                if (g.getGoalType() == GoalType.KPI) { hasKpi = true; break; }
            }
            if (!hasKpi) {
                throw new IllegalStateException("OKR 목표는 KPI 목표를 먼저 등록한 뒤 추가할 수 있습니다");
            }

            goal.updateAsOkr(request.getCategory(), request.getTitle(), request.getDescription(), grade);

        } else {
            throw new IllegalArgumentException("알 수 없는 목표 유형입니다");
        }
    }
}

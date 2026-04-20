package com.peoplecore.evaluation.service;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.evaluation.domain.*;
import com.peoplecore.evaluation.dto.GoalRequest;
import com.peoplecore.evaluation.dto.GoalResponse;
import com.peoplecore.evaluation.repository.GoalRepository;
import com.peoplecore.evaluation.repository.KpiTemplateRepository;
import com.peoplecore.evaluation.repository.SeasonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 목표 - 사원 목표 등록/수정 및 팀장 승인/반려
@Service
@Transactional
public class GoalService {

    private final GoalRepository goalRepository;
    private final SeasonRepository seasonRepository;
    private final EmployeeRepository employeeRepository;
    private final KpiTemplateRepository kpiTemplateRepository;

    public GoalService(GoalRepository goalRepository,
                       SeasonRepository seasonRepository, EmployeeRepository employeeRepository, KpiTemplateRepository kpiTemplateRepository) {
        this.goalRepository = goalRepository;
        this.seasonRepository = seasonRepository;
        this.employeeRepository = employeeRepository;
        this.kpiTemplateRepository = kpiTemplateRepository;
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

        // Entity -> DTO 변환
        List<GoalResponse> result = new ArrayList<>();
        for (Goal g : goals) {
            result.add(GoalResponse.from(g));
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
//        업무 등급 파싱(상중하) -- TODO 동적 디벨롭
        TaskGrade grade = parseGrade(request.getGrade());

//        껍데기 엔티티 생성 후 헬퍼로 타입별 필드 주입
        Goal goal = Goal.builder()
                .emp(employee)
                .season(openSeason)
                .approvalStatus(GoalApprovalStatus.PENDING)
                .build();
        applyRequestToGoal(goal, companyId, request, grade);

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
        boolean isDraft = goal.getSubmittedAt() == null;
        boolean isRejected = goal.getApprovalStatus() == GoalApprovalStatus.REJECTED;
        if (!isDraft && !isRejected) {
            throw new IllegalStateException("제출완료/승인된 목표는 수정할 수 없습니다");
        }

        TaskGrade grade = parseGrade(request.getGrade());
        applyRequestToGoal(goal, companyId, request, grade);

//        반려였으면 재제출 대기 상태로 리셋
        if (isRejected) {
            goal.resetToDraft();
        }
        // dirty checking 으로 자동 UPDATE
        return GoalResponse.from(goal);
    }

    //    4번 삭제 - 본인 소유 + 작성중/반려 상태만 삭제 가능
    public void deleteGoal(UUID companyId, Long empId, Long goalId) {

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
        boolean isDraft = goal.getSubmittedAt() == null;
        boolean isRejected = goal.getApprovalStatus() == GoalApprovalStatus.REJECTED;
        if (!isDraft && !isRejected) {
            throw new IllegalStateException("제출완료/승인된 목표는 삭제할 수 없습니다");
        }

        goalRepository.delete(goal);
    }

    //    5번 단건 제출 - 작성중/반려 상태만 제출 가능
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

//        작성중(submittedAt == null) 만 제출.
        List<GoalResponse> result = new ArrayList<>();
        for (Goal g : goals) {
            // 회사 일치 검증 (이중 방어)
            if (!g.getEmp().getCompany().getCompanyId().equals(companyId)) {
                continue;
            }
            // 작성중만 submit
            if (g.getSubmittedAt() == null) {
                g.submit();
            }
            result.add(GoalResponse.from(g));
        }
        return result;
    }


    // 업무등급 문자열(HIGH/MID/LOW)
    private TaskGrade parseGrade(String raw) {
        try {
            return TaskGrade.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("업무등급 값이 잘못되었습니다");
        }
    }

    // 타입별 검증 + Goal 엔티티에 필드 주입 (create/update 공용)
    private void applyRequestToGoal(Goal goal, UUID companyId, GoalRequest request, TaskGrade grade) {

        if ("KPI".equals(request.getGoalType())) {
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

        } else if ("OKR".equals(request.getGoalType())) {
            if (request.getCategory() == null || request.getCategory().isBlank()
                    || request.getTitle() == null || request.getTitle().isBlank()
                    || request.getDescription() == null || request.getDescription().isBlank()) {
                throw new IllegalArgumentException("OKR 목표는 구분과 제목, 설명이 필수입니다");
            }
            goal.updateAsOkr(request.getCategory(), request.getTitle(), request.getDescription(), grade);

        } else {
            throw new IllegalArgumentException("알 수 없는 목표 유형입니다");
        }
    }
}

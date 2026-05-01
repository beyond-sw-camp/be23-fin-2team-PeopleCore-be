package com.peoplecore.evaluation.service;

import com.peoplecore.alarm.publisher.HrAlarmPublisher;
import com.peoplecore.employee.domain.EmpRole;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.evaluation.domain.EvalGrade;
import com.peoplecore.evaluation.domain.EvalSeasonStatus;
import com.peoplecore.evaluation.domain.Season;
import com.peoplecore.evaluation.repository.EmpEvaluatorGlobalRepository;
import com.peoplecore.evaluation.repository.EvalGradeRepository;
import com.peoplecore.evaluation.repository.SeasonRepository;
import com.peoplecore.event.AlarmEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 평가자 퇴사 처리 — 진행 중 시즌의 EvalGrade evaluator snapshot 정리 + HR 관리자에게 알림.
// 알림: alarm-event 토픽으로 발송 -> collaboration-service 가 컨슘해 DB 저장 + SSE 실시간 push.
@Service
@Transactional
@Slf4j
public class EvaluatorRetirementHandler {

    private final SeasonRepository seasonRepository;
    private final EvalGradeRepository evalGradeRepository;
    private final EmpEvaluatorGlobalRepository empEvaluatorGlobalRepository;
    private final EmployeeRepository employeeRepository;
    private final HrAlarmPublisher hrAlarmPublisher;

    public EvaluatorRetirementHandler(SeasonRepository seasonRepository,
                                      EvalGradeRepository evalGradeRepository,
                                      EmpEvaluatorGlobalRepository empEvaluatorGlobalRepository,
                                      EmployeeRepository employeeRepository,
                                      HrAlarmPublisher hrAlarmPublisher) {
        this.seasonRepository = seasonRepository;
        this.evalGradeRepository = evalGradeRepository;
        this.empEvaluatorGlobalRepository = empEvaluatorGlobalRepository;
        this.employeeRepository = employeeRepository;
        this.hrAlarmPublisher = hrAlarmPublisher;
    }

    // 사원 퇴사 직후 호출. 평가자였으면 EvalGrade evaluator snapshot 정리 + 글로벌 매핑 정리 + HR 관리자 알림.
    public void handleEmployeeRetired(Employee retiredEmp) {
        if (retiredEmp == null || retiredEmp.getCompany() == null) return;

        UUID companyId = retiredEmp.getCompany().getCompanyId();
        Long retiredEmpId = retiredEmp.getEmpId();

        // 글로벌 매핑 정리 — 그 사원이 evaluator 인 row 들 삭제 (미정 상태로 되돌림)
        // 진행 중 시즌과 무관하게 항상 실행. 다음 시즌 만들 때 가드에 자동 걸림.
        empEvaluatorGlobalRepository.deleteByCompanyIdAndEvaluator_EmpId(companyId, retiredEmpId);

        // 진행 중 시즌 조회 — 없으면 EvalGrade 정리/알림 대상 없음
        Optional<Season> openSeasonOpt = seasonRepository
            .findByCompany_CompanyIdAndStatus(companyId, EvalSeasonStatus.OPEN);
        if (openSeasonOpt.isEmpty()) return;

        Season openSeason = openSeasonOpt.get();

        // 그 시즌 EvalGrade 중 evaluator_id_snapshot = retiredEmpId 인 row 들 조회
        List<EvalGrade> affected = evalGradeRepository
            .findBySeason_SeasonIdAndEvaluatorIdSnapshot(openSeason.getSeasonId(), retiredEmpId);

        if (affected.isEmpty()) {
            log.info("퇴사 사원 평가자 박제 row 없음 empId={}, seasonId={}",
                retiredEmpId, openSeason.getSeasonId());
            return;
        }

        // EvalGrade evaluator snapshot null 처리 -> 미지정 상태로 풀어 HR이 새 평가자 지정 가능
        for (EvalGrade row : affected) {
            row.clearEvaluator();
        }

        log.info("평가자 퇴사로 EvalGrade evaluator 정리 seasonId={}, retiredEmpId={}, count={}",
            openSeason.getSeasonId(), retiredEmpId, affected.size());

        // HR 관리자 empId 수집 — HR_ADMIN / HR_SUPER_ADMIN
        List<EmpRole> hrRoles = new ArrayList<>();
        hrRoles.add(EmpRole.HR_ADMIN);
        hrRoles.add(EmpRole.HR_SUPER_ADMIN);
        List<Employee> hrAdmins = employeeRepository
            .findByCompany_CompanyIdAndEmpRoleIn(companyId, hrRoles);
        List<Long> hrAdminEmpIds = new ArrayList<>();
        for (Employee admin : hrAdmins) {
            hrAdminEmpIds.add(admin.getEmpId());
        }
        if (hrAdminEmpIds.isEmpty()) {
            log.warn("HR 관리자 없음 — 알림 발송 skip companyId={}", companyId);
            return;
        }

        // alarm-event 토픽 발송 -> collaboration-service 가 DB 저장 + SSE 실시간 push
        AlarmEvent alarm = AlarmEvent.builder()
            .companyId(companyId)
            .alarmType("HR")
            .alarmTitle("평가자 지정 필요")
            .alarmContent(retiredEmp.getEmpName() + " 퇴사로 평가자가 지정되지 않은 사원 "
                + affected.size() + "명이 발생했습니다.")
            .alarmLink("/eval-admin?tab=emp-evaluator")
            .alarmRefType("EVAL_SEASON")
            .alarmRefId(openSeason.getSeasonId())
            .empIds(hrAdminEmpIds)
            .build();
        hrAlarmPublisher.publisher(alarm);

        log.info("평가자 퇴사 알림 발송 seasonId={}, hrAdminCount={}, affectedCount={}",
            openSeason.getSeasonId(), hrAdminEmpIds.size(), affected.size());
    }
}

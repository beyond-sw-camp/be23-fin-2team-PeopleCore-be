package com.peoplecore.evaluation.seasonscheduler;

import com.peoplecore.alarm.publisher.HrAlarmPublisher;
import com.peoplecore.employee.domain.EmpRole;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.evaluation.domain.Season;
import com.peoplecore.evaluation.domain.Stage;
import com.peoplecore.evaluation.domain.StageType;
import com.peoplecore.evaluation.repository.EvalGradeRepository;
import com.peoplecore.evaluation.repository.SeasonRepository;
import com.peoplecore.evaluation.repository.StageRepository;
import com.peoplecore.evaluation.service.EvalGradeService;
import com.peoplecore.event.AlarmEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 스케줄러에서 호출되는 건별 전이 작업 — 각 메서드가 독자 트랜잭션
// 1건 실패가 다른 건의 롤백을 유발하지 않도록 분리
@Component
@Slf4j
public class SeasonTransitionExecutor {

    private final SeasonRepository seasonRepository;
    private final StageRepository stageRepository;
    private final EvalGradeRepository evalGradeRepository;
    private final EvalGradeService evalGradeService;
    private final EmployeeRepository employeeRepository;
    private final HrAlarmPublisher hrAlarmPublisher;

    public SeasonTransitionExecutor(SeasonRepository seasonRepository,
                                    StageRepository stageRepository,
                                    EvalGradeRepository evalGradeRepository,
                                    EvalGradeService evalGradeService,
                                    EmployeeRepository employeeRepository,
                                    HrAlarmPublisher hrAlarmPublisher) {
        this.seasonRepository = seasonRepository;
        this.stageRepository = stageRepository;
        this.evalGradeRepository = evalGradeRepository;
        this.evalGradeService = evalGradeService;
        this.employeeRepository = employeeRepository;
        this.hrAlarmPublisher = hrAlarmPublisher;
    }

    // 시즌 종료일 경과 처리 — 이미 확정 / 자동 확정 / HR 알림 세 갈래
    @Transactional
    public void handleSeasonClose(Long seasonId) {
        Season s = seasonRepository.findById(seasonId).orElseThrow(() -> new IllegalArgumentException("시즌을 찾을 수 없습니다: " + seasonId));

        // 이미 수동 확정된 경우 close만
        if (s.getFinalizedAt() != null) {
            s.close();
            log.info("시즌 CLOSED (수동 확정 후 마감): {} (id={})", s.getName(), s.getSeasonId());
            return;
        }

        List<Long> unassignedEmpIds = evalGradeRepository.findUnassignedEmpIds(s.getSeasonId());

        if (unassignedEmpIds.isEmpty()) {
            // 자동 확정 + 종료 + 알림 (수동 확정과 동일 처리 공통 메서드)
            evalGradeService.finalizeAndNotify(s.getCompany().getCompanyId(), s.getSeasonId(), s);
            log.info("시즌 자동 확정+종료: {} (id={})", s.getName(), s.getSeasonId());
            return;
        }

        // HR 관리자 전원 알림 (매일 반복 발행, 수동 확정 시 상태 CLOSED 되어 자동 중단)
        List<Employee> admins = employeeRepository.findByCompany_CompanyIdAndEmpRoleIn(s.getCompany().getCompanyId(), List.of(EmpRole.HR_ADMIN, EmpRole.HR_SUPER_ADMIN));
        List<Long> adminEmpIds = admins.stream().map(Employee::getEmpId).toList();

        if (!adminEmpIds.isEmpty()) {
            AlarmEvent alarm = AlarmEvent.builder()
                    .companyId(s.getCompany().getCompanyId())
                    .empIds(adminEmpIds)
                    .alarmType("EVAL")
                    .alarmTitle("시즌 확정 필요")
                    .alarmContent(String.format("[%s] 종료일 경과, 미산정자 %d명 — 수동 확정이 필요합니다",
                            s.getName(), unassignedEmpIds.size()))
                    .alarmLink("/eval/grading/final-lock")
                    .alarmRefType("SEASON")
                    .alarmRefId(s.getSeasonId())
                    .build();
            hrAlarmPublisher.publisher(alarm);
        }
        log.warn("시즌 확정 보류: {} (id={}, 미산정 {}명)", s.getName(), s.getSeasonId(), unassignedEmpIds.size());
    }

    // 단계 시작일 도래 — WAITING -> IN_PROGRESS
    //  - GRADING 단계 시작 시 자동 산정(2번) + Z-score 편향보정(3번) + 강제배분(5번)
    @Transactional
    public void startStage(Long stageId) {
        Stage st = stageRepository.findById(stageId).orElseThrow(() -> new IllegalArgumentException("단계를 찾을 수 없습니다: " + stageId));
        st.start();
        log.info("단계 시작: {} (id={})", st.getName(), st.getStageId());

        // GRADING 단계 진입 시 자동 산정 + 편향보정 + 강제배분
        if (st.getType() == StageType.GRADING) {
            Season season = st.getSeason();
            Long seasonId = season.getSeasonId();
            java.util.UUID companyId = season.getCompany().getCompanyId();
            try {
                evalGradeService.calculateAutoGrades(companyId, seasonId);
                evalGradeService.applyBiasAdjustment(companyId, seasonId);
                evalGradeService.applyDistribution(companyId, seasonId, false);
                log.info("자동 산정 + 편향보정 + 강제배분 완료 (seasonId={})", seasonId);
            } catch (Exception e) {
                // 산정 실패해도 단계 시작 자체는 유지 (HR 이 수동 재실행 가능)
                log.error("자동 산정 실패 (seasonId={}): {}", seasonId, e.getMessage(), e);
            }
        }
    }

    // 단계 종료일 경과 — IN_PROGRESS -> FINISHED
    @Transactional
    public void finishStage(Long stageId) {
        Stage st = stageRepository.findById(stageId).orElseThrow(() -> new IllegalArgumentException("단계를 찾을 수 없습니다: " + stageId));
        st.finish();
        log.info("단계 마감: {} (id={})", st.getName(), st.getStageId());
    }
}

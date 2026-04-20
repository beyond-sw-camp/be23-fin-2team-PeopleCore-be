package com.peoplecore.evaluation.seasonscheduler;

import com.peoplecore.alarm.publisher.HrAlarmPublisher;
import com.peoplecore.employee.domain.EmpRole;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.evaluation.domain.EvalSeasonStatus;
import com.peoplecore.evaluation.domain.Season;
import com.peoplecore.evaluation.domain.Stage;
import com.peoplecore.evaluation.domain.StageStatus;
import com.peoplecore.evaluation.repository.EvalGradeRepository;
import com.peoplecore.evaluation.repository.SeasonRepository;
import com.peoplecore.evaluation.repository.StageRepository;
import com.peoplecore.evaluation.service.EvaluationRulesService;
import com.peoplecore.evaluation.service.SeasonService;
import com.peoplecore.event.AlarmEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class SeasonScheduler {
    private final SeasonRepository seasonRepository;
    private final StageRepository stageRepository;
    private final EvaluationRulesService rulesService;
    private final SeasonService seasonService;
    private final EvalGradeRepository evalGradeRepository;
    private final EmployeeRepository employeeRepository;
    private final HrAlarmPublisher hrAlarmPublisher;


    public SeasonScheduler(SeasonRepository seasonRepository, StageRepository stageRepository,
                           EvaluationRulesService rulesService, SeasonService seasonService,
                           EvalGradeRepository evalGradeRepository, EmployeeRepository employeeRepository,
                           HrAlarmPublisher hrAlarmPublisher) {
        this.seasonRepository = seasonRepository;
        this.stageRepository = stageRepository;
        this.rulesService = rulesService;
        this.seasonService = seasonService;
        this.evalGradeRepository = evalGradeRepository;
        this.employeeRepository = employeeRepository;
        this.hrAlarmPublisher = hrAlarmPublisher;
    }

//    자정 시작
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void transitionByDate(){
        LocalDate today = LocalDate.now();
        transitionSeasons(today);
        transitionStages(today);
    }

//    앱 시작 시 한 번 실행 — 자정 스케줄러 놓친 전이 메꿈
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void transitionOnStartup(){
        LocalDate today = LocalDate.now();
        transitionSeasons(today);
        transitionStages(today);
        log.info("시작 시 상태 전이 완료 (today={})", today);
    }
//시즌상태 전이
    private void transitionSeasons(LocalDate today){
//        준비중 -> 오픈 +규칙 스냅샷 동결 +EvalGrade row 일괄 생성
        List<Season> toOpen =seasonRepository.findByStatusAndStartDateLessThanEqual(EvalSeasonStatus.DRAFT,today);
        for(Season s : toOpen){
            seasonService.openSeason(s.getSeasonId()); // 상태전이 + 규칙동결 + 사원 row 생성
            log.info("시즌 OPEN: {} (id={})", s.getName(), s.getSeasonId()); // 중요일정 디버깅용
        }

//        open -> closed(종료일) — 미산정자 검사 후 자동 확정 or HR 알림
        List<Season> toClose = seasonRepository.findByStatusAndEndDateBefore(EvalSeasonStatus.OPEN, today);
        for (Season s : toClose) {
//            이미 수동 확정된 경우 close만
            if (s.getFinalizedAt() != null) {
                s.close();
                log.info("시즌 CLOSED (수동 확정 후 마감): {} (id={})", s.getName(), s.getSeasonId());
                continue;
            }

            List<Long> unassignedEmpIds = evalGradeRepository.findUnassignedEmpIds(s.getSeasonId());

            if (unassignedEmpIds.isEmpty()) {
//                자동 확정 + 종료 (현재 보정 상태 그대로 박제)
                LocalDateTime now = LocalDateTime.now();
                evalGradeRepository.lockAllAssigned(s.getSeasonId(), now);
                s.markFinalized(now);
                s.close();
                log.info("시즌 자동 확정+종료: {} (id={})", s.getName(), s.getSeasonId());
            } else {
//                HR 관리자 전원 알림 (매일 반복 발행, 수동 확정 시 상태 CLOSED 되어 자동 중단)
                List<Employee> admins = employeeRepository.findByCompany_CompanyIdAndEmpRoleIn(
                        s.getCompany().getCompanyId(),
                        List.of(EmpRole.HR_ADMIN, EmpRole.HR_SUPER_ADMIN));
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
                log.warn("시즌 확정 보류: {} (id={}, 미산정 {}명)",
                        s.getName(), s.getSeasonId(), unassignedEmpIds.size());
            }
        }
    }

    //        단계상태 전이
    private void transitionStages(LocalDate today){
//        대기 -> 진행중
        List<Stage>toStart = stageRepository.findReadyToStart(StageStatus.WAITING, today);
        for(Stage st : toStart){
            st.start();
            log.info("단계 시작: {} (id={})", st.getName(), st.getStageId());
        }

//        진행중 -> 마감
        List<Stage>toFinish = stageRepository.findReadyToFinish(StageStatus.IN_PROGRESS, today);
        for(Stage st : toFinish){
            st.finish();
            log.info("단계 마감: {} (id={})", st.getName(), st.getStageId());
        }

    }


}

package com.peoplecore.evaluation.seasonscheduler;

import com.peoplecore.evaluation.domain.EvalSeasonStatus;
import com.peoplecore.evaluation.domain.Season;
import com.peoplecore.evaluation.domain.Stage;
import com.peoplecore.evaluation.domain.StageStatus;
import com.peoplecore.evaluation.repository.SeasonRepository;
import com.peoplecore.evaluation.repository.StageRepository;
import com.peoplecore.evaluation.service.SeasonService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@Slf4j
public class SeasonScheduler {
    private final SeasonRepository seasonRepository;
    private final StageRepository stageRepository;
    private final SeasonService seasonService;
    private final SeasonTransitionExecutor transitionExecutor;


    public SeasonScheduler(SeasonRepository seasonRepository, StageRepository stageRepository,
                           SeasonService seasonService, SeasonTransitionExecutor transitionExecutor) {
        this.seasonRepository = seasonRepository;
        this.stageRepository = stageRepository;
        this.seasonService = seasonService;
        this.transitionExecutor = transitionExecutor;
    }

//    자정 시작 — 건별 트랜잭션은 executor/SeasonService 각자 보유
    @Scheduled(cron = "0 0 0 * * *")
    public void transitionByDate(){
        LocalDate today = LocalDate.now();
        transitionSeasons(today);
        transitionStages(today);
    }

//    앱 시작 시 한 번 실행 — 자정 스케줄러 놓친 전이 메꿈
    @EventListener(ApplicationReadyEvent.class)
    public void transitionOnStartup(){
        LocalDate today = LocalDate.now();
        transitionSeasons(today);
        transitionStages(today);
        log.info("시작 시 상태 전이 완료 (today={})", today);
    }

//    TODO: 지우기
//    수동 실행 (임시/개발용) — 프론트에서 즉시 전이가 필요할 때 호출
    public void runNow(){
        LocalDate today = LocalDate.now();
        transitionSeasons(today);
        transitionStages(today);
        log.info("수동 스케줄러 실행 완료 (today={})", today);
    }
//시즌상태 전이 — 1건 실패가 다른 건을 막지 않도록 try/catch 로 격리
    private void transitionSeasons(LocalDate today){
//        준비중 -> 오픈 (상태전이 + 규칙동결 + 사원 row 생성, SeasonService 자체 @Transactional)
        List<Season> toOpen = seasonRepository.findByStatusAndStartDateLessThanEqual(EvalSeasonStatus.DRAFT, today);
        for (Season s : toOpen) {
            try {
                seasonService.openSeason(s.getSeasonId());
                log.info("시즌 OPEN: {} (id={})", s.getName(), s.getSeasonId());
            } catch (Exception e) {
                log.error("시즌 OPEN 실패 (id={})", s.getSeasonId(), e);
            }
        }

//        open -> closed(종료일) — executor 에 위임 (이미 확정 / 자동 확정 / HR 알림 분기는 그쪽에서)
        List<Season> toClose = seasonRepository.findByStatusAndEndDateBefore(EvalSeasonStatus.OPEN, today);
        for (Season s : toClose) {
            try {
                transitionExecutor.handleSeasonClose(s.getSeasonId());
            } catch (Exception e) {
                log.error("시즌 종료 처리 실패 (id={})", s.getSeasonId(), e);
            }
        }
    }

    //        단계상태 전이 — 건별 트랜잭션, 예외 격리
    private void transitionStages(LocalDate today){
//        대기 -> 진행중
        List<Stage> toStart = stageRepository.findReadyToStart(StageStatus.WAITING, today);
        for (Stage st : toStart) {
            try {
                transitionExecutor.startStage(st.getStageId());
            } catch (Exception e) {
                log.error("단계 시작 실패 (id={})", st.getStageId(), e);
            }
        }

//        진행중 -> 마감
        List<Stage> toFinish = stageRepository.findReadyToFinish(StageStatus.IN_PROGRESS, today);
        for (Stage st : toFinish) {
            try {
                transitionExecutor.finishStage(st.getStageId());
            } catch (Exception e) {
                log.error("단계 마감 실패 (id={})", st.getStageId(), e);
            }
        }

    }


}

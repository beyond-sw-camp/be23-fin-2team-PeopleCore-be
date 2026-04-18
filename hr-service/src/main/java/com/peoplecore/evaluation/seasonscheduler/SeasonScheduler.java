package com.peoplecore.evaluation.seasonscheduler;

import com.peoplecore.evaluation.domain.EvalSeasonStatus;
import com.peoplecore.evaluation.domain.Season;
import com.peoplecore.evaluation.domain.Stage;
import com.peoplecore.evaluation.domain.StageStatus;
import com.peoplecore.evaluation.repository.SeasonRepository;
import com.peoplecore.evaluation.repository.StageRepository;
import com.peoplecore.evaluation.service.EvaluationRulesService;
import com.peoplecore.evaluation.service.SeasonService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@Slf4j
public class SeasonScheduler {
    private final SeasonRepository seasonRepository;
    private final StageRepository stageRepository;
    private final EvaluationRulesService rulesService;
    private final SeasonService seasonService;


    public SeasonScheduler(SeasonRepository seasonRepository, StageRepository stageRepository, EvaluationRulesService rulesService, SeasonService seasonService) {
        this.seasonRepository = seasonRepository;
        this.stageRepository = stageRepository;
        this.rulesService = rulesService;
        this.seasonService = seasonService;
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

//        open -> closed(종료일)
        List<Season>toClose = seasonRepository.findByStatusAndEndDateBefore(EvalSeasonStatus.OPEN,today);
        for(Season s : toClose){
            s.close();
            log.info("시즌 CLOSED: {} (id={})", s.getName(), s.getSeasonId());
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

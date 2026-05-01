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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Component
@Slf4j
public class SeasonScheduler {

    private static final Duration LOCK_TTL = Duration.ofMinutes(10);
    private static final String LOCK_KEY_DAILY = "season-transition";
    private static final String LOCK_KEY_STARTUP = "season-startup-transition";

    private final StringRedisTemplate redisTemplate;
    private final SeasonRepository seasonRepository;
    private final StageRepository stageRepository;
    private final SeasonService seasonService;
    private final SeasonTransitionExecutor transitionExecutor;


    public SeasonScheduler(StringRedisTemplate redisTemplate,
                           SeasonRepository seasonRepository, StageRepository stageRepository,
                           SeasonService seasonService, SeasonTransitionExecutor transitionExecutor) {
        this.redisTemplate = redisTemplate;
        this.seasonRepository = seasonRepository;
        this.stageRepository = stageRepository;
        this.seasonService = seasonService;
        this.transitionExecutor = transitionExecutor;
    }

//    자정 시작 — 건별 트랜잭션은 executor/SeasonService 각자 보유
//    분산 락으로 멀티 인스턴스 중복 실행 방지 (락 키: season-transition:{yyyy-MM-dd})
    @Scheduled(cron = "0 0 0 * * *")
    public void transitionByDate(){
        LocalDate today = LocalDate.now();
        String lockKey = LOCK_KEY_DAILY + ":" + today;

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("[SeasonTransition] 다른 인스턴스 진행 중 - skip. date={}", today);
            return;
        }
        log.info("[SeasonTransition] 시작 - date={}", today);
        try {
            transitionSeasons(today);
            transitionStages(today);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

//    앱 시작 시 한 번 실행 — 자정 스케줄러 놓친 전이 메꿈
//    분산 락으로 N개 파드 동시 부팅 시 중복 실행 방지 (락 키: season-startup-transition:{yyyy-MM-dd})
    @EventListener(ApplicationReadyEvent.class)
    public void transitionOnStartup(){
        LocalDate today = LocalDate.now();
        String lockKey = LOCK_KEY_STARTUP + ":" + today;

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("[SeasonStartup] 다른 인스턴스 진행 중 - skip. date={}", today);
            return;
        }
        log.info("[SeasonStartup] 시작 - date={}", today);
        try {
            transitionSeasons(today);
            transitionStages(today);
            log.info("시작 시 상태 전이 완료 (today={})", today);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

//    TODO: 지우기
//    test용 시즌 수동 실행 — 시즌 OPEN + 1단계 자동 시작만 수행. 나머지 단계는 WAITING 유지하고 단계 버튼으로 전진.
    public void runNow(){
        LocalDate today = LocalDate.now();
        transitionSeasons(today);
        log.info("수동 시즌 스케줄러 실행 완료 (today={})", today);
    }

//    TODO: 지우기
//    test용 단일 단계 수동 실행 — 날짜 무시하고 즉시 다음 상태로.
//      WAITING -> IN_PROGRESS: 같은 시즌의 IN_PROGRESS 단계를 먼저 마감 후 이 단계 시작
//      IN_PROGRESS -> FINISHED: 그대로 마감
//      FINISHED 는 무시
    public void runStageNow(Long stageId){
        Stage st = stageRepository.findById(stageId)
                .orElseThrow(() -> new IllegalArgumentException("단계를 찾을 수 없습니다: " + stageId));
        StageStatus current = st.getStatus();
        if (current == StageStatus.WAITING) {
//            같은 시즌에 진행중인 단계가 있으면 먼저 마감 (한 시즌에 IN_PROGRESS 는 하나만 유지)
            Long seasonId = st.getSeason().getSeasonId();
            List<Stage> siblings = stageRepository.findBySeason_SeasonId(seasonId);
            for (Stage prev : siblings) {
                if (prev.getStatus() == StageStatus.IN_PROGRESS) {
                    try {
                        transitionExecutor.finishStage(prev.getStageId());
                        log.info("이전 단계 자동 마감: {} (id={})", prev.getName(), prev.getStageId());
                    } catch (Exception e) {
                        log.error("이전 단계 자동 마감 실패 (id={})", prev.getStageId(), e);
                        throw e;
                    }
                }
            }
            try {
                transitionExecutor.startStage(stageId);
                log.info("단계 수동 시작: {} (id={})", st.getName(), stageId);
            } catch (Exception e) {
                log.error("단계 수동 시작 실패 (id={})", stageId, e);
                throw e;
            }
        } else if (current == StageStatus.IN_PROGRESS) {
            try {
                transitionExecutor.finishStage(stageId);
                log.info("단계 수동 마감: {} (id={})", st.getName(), stageId);
            } catch (Exception e) {
                log.error("단계 수동 마감 실패 (id={})", stageId, e);
                throw e;
            }
        }
    }
//시즌상태 전이 — 1건 실패가 다른 건을 막지 않도록 try/catch 로 격리
    private void transitionSeasons(LocalDate today){
//        준비중 -> 오픈 (상태전이 + 규칙동결 + 사원 row 생성, SeasonService 자체 @Transactional)
        List<Season> toOpen = seasonRepository.findByStatusAndStartDateLessThanEqual(EvalSeasonStatus.DRAFT, today);
        for (Season s : toOpen) {
            try {
                seasonService.openSeason(s.getSeasonId());
                log.info("시즌 OPEN: {} (id={})", s.getName(), s.getSeasonId());
                autoStartFirstStage(s.getSeasonId());
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

//    시즌 OPEN 직후 1단계(orderNo 최소 WAITING) 자동 시작
    private void autoStartFirstStage(Long seasonId){
        stageRepository.findBySeason_SeasonId(seasonId).stream()
                .filter(st -> st.getStatus() == StageStatus.WAITING)
                .filter(st -> st.getOrderNo() != null)
                .min(Comparator.comparing(Stage::getOrderNo))
                .ifPresent(first -> {
                    try {
                        transitionExecutor.startStage(first.getStageId());
                        log.info("시즌 OPEN 시 1단계 자동 시작: {} (id={})", first.getName(), first.getStageId());
                    } catch (Exception e) {
                        log.error("1단계 자동 시작 실패 (id={})", first.getStageId(), e);
                    }
                });
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

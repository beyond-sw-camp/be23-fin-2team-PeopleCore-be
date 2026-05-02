package com.peoplecore.vacation.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/* Quartz 가 fire 시각에 호출하는 월차→연차 전환 잡 진입점 */
/* AnnualTransitionScheduler.run() 위임 — 본체 로직 공유 */
/* 예외 흡수 + 로깅. JobExecutionException 안 던짐 (misfire DO_NOTHING 일관) */
@Slf4j
public class AnnualTransitionJob implements Job {

    @Autowired
    private AnnualTransitionScheduler scheduler;

    @Override
    public void execute(JobExecutionContext context) {
        try {
            scheduler.run();
        } catch (Exception e) {
            log.error("[AnnualTransitionJob] 실행 실패", e);
        }
    }
}

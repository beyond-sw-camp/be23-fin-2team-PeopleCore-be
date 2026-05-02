package com.peoplecore.vacation.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/* Quartz 가 fire 시각에 호출하는 생리휴가 적립 잡 진입점 */
/* MenstrualMonthlyGrantScheduler.run() 위임 — 본체 로직 공유 */
/* 예외 흡수 + 로깅. JobExecutionException 안 던짐 (misfire DO_NOTHING 일관) */
@Slf4j
public class MenstrualMonthlyGrantJob implements Job {

    @Autowired
    private MenstrualMonthlyGrantScheduler scheduler;

    @Override
    public void execute(JobExecutionContext context) {
        try {
            scheduler.run();
        } catch (Exception e) {
            log.error("[MenstrualMonthlyGrantJob] 실행 실패", e);
        }
    }
}

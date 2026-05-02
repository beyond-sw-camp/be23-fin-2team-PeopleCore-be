package com.peoplecore.vacation.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/* Quartz 가 fire 시각에 호출하는 연차 발생 잡 진입점 */
/* AnnualGrantScheduler.run() 위임 — 본체 로직 공유 (HIRE/FISCAL 분기 포함) */
/* 예외 흡수 + 로깅. JobExecutionException 안 던짐 (misfire DO_NOTHING 일관) */
@Slf4j
public class AnnualGrantJob implements Job {

    @Autowired
    private AnnualGrantScheduler scheduler;

    @Override
    public void execute(JobExecutionContext context) {
        try {
            scheduler.run();
        } catch (Exception e) {
            log.error("[AnnualGrantJob] 실행 실패", e);
        }
    }
}

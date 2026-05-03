package com.peoplecore.vacation.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

/* Quartz 가 fire 시각에 호출하는 만료 Batch 런처 잡 진입점 */
/* BalanceExpiryScheduler.run() 위임 — 본체 로직 공유 */
/* 예외 발생 시 ERROR 로그 + JobExecutionException 변환 throw → JobFailureNotifier 가 Discord 알림 */
@Slf4j
public class BalanceExpiryJob implements Job {

    @Autowired
    private BalanceExpiryScheduler scheduler;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            scheduler.run();
        } catch (Exception e) {
            log.error("[BalanceExpiryJob] 실행 실패", e);
            throw new JobExecutionException(e, false);  // false = refireImmediately X
        }
    }
}

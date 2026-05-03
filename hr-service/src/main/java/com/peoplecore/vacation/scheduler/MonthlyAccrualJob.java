package com.peoplecore.vacation.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

/* Quartz 가 fire 시각에 호출하는 월차 적립 잡 진입점 */
/* MonthlyAccrualScheduler.run() 위임 — 본체 로직 공유 (정기/수동 동작 일관) */
/* 멀티 노드 한 대만 fire = QRTZ_LOCKS row lock 보장 */
/* 예외 발생 시 ERROR 로그 + JobExecutionException 변환 throw → JobFailureNotifier 가 Discord 알림 */
/* Quartz 가 매 fire 마다 새 인스턴스 생성 → 빈 생성자 + 필드 @Autowired (생성자 주입 불가) */
@Slf4j
public class MonthlyAccrualJob implements Job {

    @Autowired
    private MonthlyAccrualScheduler scheduler;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            scheduler.run();
        } catch (Exception e) {
            log.error("[MonthlyAccrualJob] 실행 실패", e);
            throw new JobExecutionException(e, false);  // false = refireImmediately X
        }
    }
}

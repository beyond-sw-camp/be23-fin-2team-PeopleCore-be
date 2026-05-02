package com.peoplecore.vacation.scheduler;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.ObjectAlreadyExistsException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/* 만료 Batch 잡의 Quartz 등록 전담 (SRP — 등록만) */
/* 매일 00:20 KST fire, misfire = DO_NOTHING (만료 두 번 발생 가능성 보수적 차단) */
@Slf4j
@Configuration
public class BalanceExpirySchedulerConfig {

    private static final String JOB_GROUP = "vacation";
    private static final String JOB_NAME = "balance-expiry";
    private static final String CRON = "0 20 0 * * ?";   // 매일 00:20:00 KST
    private static final TimeZone TZ_SEOUL = TimeZone.getTimeZone("Asia/Seoul");

    private final Scheduler scheduler;

    @Autowired
    public BalanceExpirySchedulerConfig(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @PostConstruct
    public void register() {
        JobKey jobKey = JobKey.jobKey(JOB_NAME, JOB_GROUP);
        TriggerKey triggerKey = TriggerKey.triggerKey(JOB_NAME, JOB_GROUP);

        try {
            if (scheduler.checkExists(triggerKey)) {
                CronTrigger existing = (CronTrigger) scheduler.getTrigger(triggerKey);
                if (existing != null && CRON.equals(existing.getCronExpression())) {
                    log.debug("[BalanceExpiryScheduler] 동일 cron — skip");
                    return;
                }
                scheduler.rescheduleJob(triggerKey, buildTrigger(triggerKey, jobKey));
                log.info("[BalanceExpiryScheduler] 재등록 — cron={}", CRON);
                return;
            }

            JobDetail jobDetail = JobBuilder.newJob(BalanceExpiryJob.class)
                    .withIdentity(jobKey)
                    .storeDurably()
                    .build();
            scheduler.addJob(jobDetail, true);
            scheduler.scheduleJob(buildTrigger(triggerKey, jobKey));
            log.info("[BalanceExpiryScheduler] 신규 등록 — cron={}", CRON);

        } catch (ObjectAlreadyExistsException race) {
            log.info("[BalanceExpiryScheduler] 등록 race 감지 — 다른 노드 선등록");
        } catch (SchedulerException e) {
            log.error("[BalanceExpiryScheduler] 등록 실패", e);
        }
    }

    private CronTrigger buildTrigger(TriggerKey triggerKey, JobKey jobKey) {
        return TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobKey)
                .withSchedule(CronScheduleBuilder.cronSchedule(CRON)
                        .inTimeZone(TZ_SEOUL)
                        .withMisfireHandlingInstructionDoNothing())
                .build();
    }
}

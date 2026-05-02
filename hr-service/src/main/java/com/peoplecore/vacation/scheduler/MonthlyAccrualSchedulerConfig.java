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

/* 월차 적립 잡의 Quartz 등록 전담 (SRP — 등록만) */
/* 매일 00:00 KST fire, misfire = DO_NOTHING (Service 직접 호출이라 보수적, Phase 2 후 FIRE_NOW 상향 가능) */
@Slf4j
@Configuration
public class MonthlyAccrualSchedulerConfig {

    private static final String JOB_GROUP = "vacation";
    private static final String JOB_NAME = "monthly-accrual";
    private static final String CRON = "0 0 0 * * ?";   // 매일 00:00:00 KST
    private static final TimeZone TZ_SEOUL = TimeZone.getTimeZone("Asia/Seoul");

    private final Scheduler scheduler;

    @Autowired
    public MonthlyAccrualSchedulerConfig(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @PostConstruct
    public void register() {
        JobKey jobKey = JobKey.jobKey(JOB_NAME, JOB_GROUP);
        TriggerKey triggerKey = TriggerKey.triggerKey(JOB_NAME, JOB_GROUP);

        try {
            // 트리거 존재 + cron 동일 → skip / cron 변경 → reschedule
            if (scheduler.checkExists(triggerKey)) {
                CronTrigger existing = (CronTrigger) scheduler.getTrigger(triggerKey);
                if (existing != null && CRON.equals(existing.getCronExpression())) {
                    log.debug("[MonthlyAccrualScheduler] 동일 cron — skip");
                    return;
                }
                scheduler.rescheduleJob(triggerKey, buildTrigger(triggerKey, jobKey));
                log.info("[MonthlyAccrualScheduler] 재등록 — cron={}", CRON);
                return;
            }

            JobDetail jobDetail = JobBuilder.newJob(MonthlyAccrualJob.class)
                    .withIdentity(jobKey)
                    .storeDurably()
                    .build();
            scheduler.addJob(jobDetail, true);
            scheduler.scheduleJob(buildTrigger(triggerKey, jobKey));
            log.info("[MonthlyAccrualScheduler] 신규 등록 — cron={}", CRON);

        } catch (ObjectAlreadyExistsException race) {
            log.info("[MonthlyAccrualScheduler] 등록 race 감지 — 다른 노드 선등록");
        } catch (SchedulerException e) {
            log.error("[MonthlyAccrualScheduler] 등록 실패", e);
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

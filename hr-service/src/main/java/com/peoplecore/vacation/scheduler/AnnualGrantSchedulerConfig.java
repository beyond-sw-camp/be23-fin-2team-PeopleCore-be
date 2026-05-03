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

/* 연차 발생 잡의 Quartz 등록 전담 (SRP — 등록만) */
/* 매일 00:10 KST fire, misfire = FIRE_NOW (HIRE/FISCAL 모두 JobLauncher 위임 + JobInstance(companyId, targetDate) UNIQUE 제약으로 중복 발생 자동 차단) */
@Slf4j
@Configuration
public class AnnualGrantSchedulerConfig {

    private static final String JOB_GROUP = "vacation";
    private static final String JOB_NAME = "annual-grant";
    private static final String CRON = "0 10 0 * * ?";   // 매일 00:10:00 KST (AnnualTransition 직후)
    private static final TimeZone TZ_SEOUL = TimeZone.getTimeZone("Asia/Seoul");

    private final Scheduler scheduler;

    @Autowired
    public AnnualGrantSchedulerConfig(Scheduler scheduler) {
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
                    log.debug("[AnnualGrantScheduler] 동일 cron — skip");
                    return;
                }
                scheduler.rescheduleJob(triggerKey, buildTrigger(triggerKey, jobKey));
                log.info("[AnnualGrantScheduler] 재등록 — cron={}", CRON);
                return;
            }

            JobDetail jobDetail = JobBuilder.newJob(AnnualGrantJob.class)
                    .withIdentity(jobKey)
                    .storeDurably()
                    .build();
            scheduler.addJob(jobDetail, true);
            scheduler.scheduleJob(buildTrigger(triggerKey, jobKey));
            log.info("[AnnualGrantScheduler] 신규 등록 — cron={}", CRON);

        } catch (ObjectAlreadyExistsException race) {
            log.info("[AnnualGrantScheduler] 등록 race 감지 — 다른 노드 선등록");
        } catch (SchedulerException e) {
            log.error("[AnnualGrantScheduler] 등록 실패", e);
        }
    }

    private CronTrigger buildTrigger(TriggerKey triggerKey, JobKey jobKey) {
        return TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobKey)
                .withSchedule(CronScheduleBuilder.cronSchedule(CRON)
                        .inTimeZone(TZ_SEOUL)
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }
}

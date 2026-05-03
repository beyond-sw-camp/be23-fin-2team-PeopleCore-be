package com.peoplecore.common.quartz;

import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/* 전역 JobListener — 잡 실패 시 Discord 알림 */
/* QuartzListenerRegistrar 가 setGlobalJobListeners 로 전역 등록 → 모든 잡 자동 적용 */
/* 실패 판단: jobWasExecuted 의 jobException 이 null 이 아닐 때 */
/* Job 클래스들이 catch 후 JobExecutionException 던지면 여기로 흘러옴 */
@Slf4j
@Component
public class JobFailureNotifier implements JobListener {

    private static final String LISTENER_NAME = "JobFailureNotifier";

    private final DiscordWebhookSender sender;

    @Autowired
    public JobFailureNotifier(DiscordWebhookSender sender) {
        this.sender = sender;
    }

    @Override
    public String getName() {
        return LISTENER_NAME;
    }

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        // 미사용 — 시작 알림 필요 시 여기 확장
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {
        // 미사용 — TriggerListener 가 fire 차단한 경우
    }

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        if (jobException == null) return;  // 성공 시 알림 안 함

        String jobKey = context.getJobDetail().getKey().toString();
        String fireTime = context.getFireTime().toString();
        long runTimeMs = context.getJobRunTime();
        String cause = jobException.getMessage();

        String msg = String.format(
                ":rotating_light: **잡 실패** — %s%nfire: %s%nrunTime: %dms%ncause: %s",
                jobKey, fireTime, runTimeMs, cause);

        log.error("[JobFailureNotifier] 알림 발사 - {}", jobKey, jobException);
        sender.send(msg);
    }
}

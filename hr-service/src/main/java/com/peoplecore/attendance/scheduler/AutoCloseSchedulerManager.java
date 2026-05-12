package com.peoplecore.attendance.scheduler;

import com.peoplecore.attendance.entity.WorkGroup;
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
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.Set;
import java.util.TimeZone;

/*
 * 단일 JobDetail + wg 별 Trigger 패턴.
 * @PostConstruct: 마이그레이션 cleanup + JobDetail 1회 등록.
 * register/unregister: wg 별 Trigger 동적 관리.
 */
@Component
@Slf4j
public class AutoCloseSchedulerManager {

    private static final String JOB_GROUP = "auto-close";
    private static final String JOB_NAME = "auto-close-job";    // 단일 JobDetail
    private static final String TRIGGER_GROUP = "auto-close";
    private static final String KEY_WORK_GROUP_ID = "workGroupId";  // AutoCloseJob.KEY_* 와 일치
    private static final String KEY_COMPANY_ID = "companyId";
    private static final String LEGACY_DISPATCHER_GROUP = "auto-close-dispatcher";  // 구 디스패처 정리용
    private static final TimeZone TZ_SEOUL = TimeZone.getTimeZone("Asia/Seoul");

    private final Scheduler scheduler;

    @Autowired
    public AutoCloseSchedulerManager(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @PostConstruct
    public void init() {
        cleanupLegacy();
        ensureJobDetail();
    }

    /* 구버전 wg-* JobDetail + 디스패처 잡 정리. race-safe (멀티 파드 동시 부팅) */
    private void cleanupLegacy() {
        try {
            Set<JobKey> sameGroup = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(JOB_GROUP));
            int removed = 0;
            for (JobKey jk : sameGroup) {
                if (!JOB_NAME.equals(jk.getName())) {  // 새 단일 JobDetail 외 전부 제거
                    scheduler.deleteJob(jk);
                    removed++;
                }
            }
            if (removed > 0) log.info("[AutoCloseScheduler] legacy per-wg JobDetail 정리 — count={}", removed);

            Set<JobKey> dispatcher = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(LEGACY_DISPATCHER_GROUP));
            for (JobKey jk : dispatcher) scheduler.deleteJob(jk);
            if (!dispatcher.isEmpty()) log.info("[AutoCloseScheduler] legacy dispatcher 정리 — count={}", dispatcher.size());
        } catch (SchedulerException e) {
            log.error("[AutoCloseScheduler] legacy 정리 실패", e);
        }
    }

    /* 단일 JobDetail 멱등 등록. storeDurably — Trigger 없어도 유지 */
    private void ensureJobDetail() {
        JobKey jobKey = JobKey.jobKey(JOB_NAME, JOB_GROUP);
        try {
            JobDetail jobDetail = JobBuilder.newJob(AutoCloseJob.class)
                    .withIdentity(jobKey)
                    .storeDurably()
                    .build();
            scheduler.addJob(jobDetail, true);  // replace=true → 멱등
            log.info("[AutoCloseScheduler] JobDetail 등록 — {}", jobKey);
        } catch (SchedulerException e) {
            log.error("[AutoCloseScheduler] JobDetail 등록 실패", e);
        }
    }

    /* wg 별 Trigger 멱등 등록. JobDataMap 에 wg 정보 세팅 */
    public void register(WorkGroup wg) {
        if (wg == null) return;
        Long workGroupId = wg.getWorkGroupId();

        if (wg.getGroupDeleteAt() != null) {  // soft delete → Trigger 해제만
            unregister(workGroupId);
            return;
        }
        if (wg.getGroupStartTime() == null) {  // 데이터 이상 — 등록 안 함
            log.warn("[AutoCloseScheduler] groupStartTime null — skip. workGroupId={}", workGroupId);
            return;
        }

        String cron = toCronExpression(wg.getGroupStartTime());
        String companyId = wg.getCompany().getCompanyId().toString();
        TriggerKey triggerKey = triggerKeyOf(workGroupId);
        JobKey jobKey = JobKey.jobKey(JOB_NAME, JOB_GROUP);

        try {
            if (scheduler.checkExists(triggerKey)) {
                CronTrigger existing = (CronTrigger) scheduler.getTrigger(triggerKey);
                if (existing != null && cron.equals(existing.getCronExpression())) return;  // 동일 cron skip
                scheduler.rescheduleJob(triggerKey, buildTrigger(triggerKey, jobKey, cron, workGroupId, companyId));
                log.info("[AutoCloseScheduler] Trigger 재등록 — workGroupId={}, cron={}", workGroupId, cron);
                return;
            }
            scheduler.scheduleJob(buildTrigger(triggerKey, jobKey, cron, workGroupId, companyId));
            log.info("[AutoCloseScheduler] Trigger 신규 — workGroupId={}, cron={}", workGroupId, cron);
        } catch (ObjectAlreadyExistsException race) {
            // 멀티 파드 동시 부팅 시 다른 노드 선등록 — 정상
            log.info("[AutoCloseScheduler] Trigger 등록 race — workGroupId={}", workGroupId);
        } catch (SchedulerException e) {
            log.error("[AutoCloseScheduler] Trigger 등록 실패 — workGroupId={}", workGroupId, e);
        }
    }

    /* wg 별 Trigger 해제. JobDetail 은 다른 wg 공유라 유지 */
    public void unregister(Long workGroupId) {
        if (workGroupId == null) return;
        try {
            if (scheduler.unscheduleJob(triggerKeyOf(workGroupId))) {
                log.info("[AutoCloseScheduler] Trigger 해제 — workGroupId={}", workGroupId);
            }
        } catch (SchedulerException e) {
            log.error("[AutoCloseScheduler] Trigger 해제 실패 — workGroupId={}", workGroupId, e);
        }
    }

    /* groupStartTime - 2h → Quartz 6필드 cron. 01:00 → "0 0 23 * * ?" (전날 wrap) */
    static String toCronExpression(LocalTime startTime) {
        LocalTime fireAt = startTime.minusHours(2);
        return String.format("0 %d %d * * ?", fireAt.getMinute(), fireAt.getHour());
    }

    private CronTrigger buildTrigger(TriggerKey triggerKey, JobKey jobKey, String cron,
                                     Long workGroupId, String companyId) {
        return TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobKey)
                .usingJobData(KEY_WORK_GROUP_ID, workGroupId)
                .usingJobData(KEY_COMPANY_ID, companyId)
                .withSchedule(CronScheduleBuilder.cronSchedule(cron)
                        .inTimeZone(TZ_SEOUL)  // EKS UTC 우회
                        .withMisfireHandlingInstructionDoNothing())  // 자동마감 멱등 보장 X
                .build();
    }

    private static TriggerKey triggerKeyOf(Long workGroupId) {
        return TriggerKey.triggerKey("wg-" + workGroupId, TRIGGER_GROUP);
    }
}

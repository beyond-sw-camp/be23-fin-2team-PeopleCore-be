package com.peoplecore.attendance.scheduler;

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

/*
 * 파티션 사전 생성 잡의 Quartz 등록 전담 설정 (SRP — 등록만).
 *
 * 부팅 시 1회 호출되어 Quartz Scheduler 의 QRTZ_JOB_DETAILS / QRTZ_TRIGGERS 에
 * "잡 정의 + 트리거" 한 쌍을 INSERT. 이후 매월 25일 03:00 KST 마다 Quartz 가
 * 알아서 PartitionEnsureJob.execute() 를 fire. 이 클래스는 그 후 아무 일도 안 함.
 *
 * Misfire = FIRE_NOW : PartitionEnsureService 가 COUNT 후 DDL 이라 멱등.
 * 누락 시 즉시 따라잡아도 안전.
 */
@Slf4j
@Configuration
public class PartitionSchedulerConfig {

    // Quartz Key 의 group 부분. 도메인별 잡 격리용 (auto-close, partition, vacation 식 구분)
    private static final String JOB_GROUP = "partition";

    // JobDetail/Trigger 식별자. 파티션 잡은 단일이라 고정 문자열. (AutoClose 는 wg-{id} 처럼 동적)
    private static final String JOB_NAME = "partition-ensure";

    // Quartz cron = 6필드 (초 분 시 일 월 요일). 일=25, 요일=? (둘 중 하나는 ? 강제)
    // → 매월 25일 03:00:00 KST
    private static final String CRON = "0 0 3 25 * ?";

    // EKS 워커 노드 기본 timezone 은 UTC. 명시 누락 시 9시간 어긋남 (03:00 → KST 12:00 fire)
    private static final TimeZone TZ_SEOUL = TimeZone.getTimeZone("Asia/Seoul");

    // Spring Boot starter-quartz 가 자동 생성·관리하는 Scheduler 빈을 그대로 주입받음
    private final Scheduler scheduler;

    @Autowired
    public PartitionSchedulerConfig(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /*
     * 부팅 시 1회 register. AutoCloseStartupLoader 와 동일 패턴.
     * Spring Boot Quartz Scheduler 는 standby 상태(아직 start 전)라도 addJob/scheduleJob 등록 가능.
     */
    @PostConstruct
    public void register() {
        // 잡과 트리거를 식별하는 키. (이름 + 그룹) 조합이 유니크.
        // 동일 키로 두 번 INSERT 시 ObjectAlreadyExistsException → 아래에서 흡수
        JobKey jobKey = JobKey.jobKey(JOB_NAME, JOB_GROUP);
        TriggerKey triggerKey = TriggerKey.triggerKey(JOB_NAME, JOB_GROUP);

        try {
            // ── 분기 1: 트리거가 이미 DB 에 있음 (재부팅 또는 멀티노드 다른 파드가 먼저 등록)
            if (scheduler.checkExists(triggerKey)) {
                CronTrigger existing = (CronTrigger) scheduler.getTrigger(triggerKey);

                // cron 이 같으면 손대지 않음 — DB 부하 0, 다음 fire 시각 유지
                if (existing != null && CRON.equals(existing.getCronExpression())) {
                    log.debug("[PartitionScheduler] 동일 cron — skip. cron={}", CRON);
                    return;
                }

                // cron 이 바뀌었으면 (코드 수정 후 재배포 시나리오) 트리거만 갱신
                // JobDetail 은 그대로, Trigger 만 새로 만들어 교체
                scheduler.rescheduleJob(triggerKey, buildTrigger(triggerKey, jobKey));
                log.info("[PartitionScheduler] 재등록 — cron={}", CRON);
                return;
            }

            // ── 분기 2: 신규 등록 (DB 갈아엎음/처음 부팅)

            // JobDetail = "어떤 클래스를 호출할지" 정의. PartitionEnsureJob.execute() 가 진입점
            // storeDurably=true → 트리거 없어도 잡 정의 유지 (재등록·트리거 갱신 안전)
            // addJob(replace=true) 의 replace 와 함께 멱등 보장
            JobDetail jobDetail = JobBuilder.newJob(PartitionEnsureJob.class)
                    .withIdentity(jobKey)
                    .storeDurably()
                    .build();

            // 잡 정의 INSERT (replace=true 라 동일 키 존재 시 덮어쓰기 — 정의 변경 시 안전)
            scheduler.addJob(jobDetail, true);

            // 트리거 INSERT — 이때부터 Quartz 가 cron 시각마다 fire 시작
            scheduler.scheduleJob(buildTrigger(triggerKey, jobKey));
            log.info("[PartitionScheduler] 신규 등록 — cron={}", CRON);

        } catch (ObjectAlreadyExistsException race) {
            // 멀티 인스턴스 동시 부팅 시 양쪽 파드가 동시에 INSERT 시도 → 한쪽만 성공.
            // checkExists 통과 후 INSERT 직전에 다른 노드가 먼저 INSERT 하면 여기로 들어옴.
            // 정상 흐름이라 흡수 + INFO 로 기록만.
            log.info("[PartitionScheduler] 등록 race 감지 — 다른 노드 선등록");
        } catch (SchedulerException e) {
            // 그 외 Quartz 예외 (DB 다운 등) — ERROR 로 남기되 부팅은 계속 (다른 잡까지 막지 않게)
            log.error("[PartitionScheduler] 등록 실패", e);
        }
    }

    /*
     * CronTrigger 빌더. 한 곳에 모아 TZ + misfire 정책 일관 적용.
     * 신규 등록·재등록 양쪽이 같은 빌더 통과 → 정책 누락 사고 차단.
     */
    private CronTrigger buildTrigger(TriggerKey triggerKey, JobKey jobKey) {
        return TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)        // 트리거 식별자
                .forJob(jobKey)                  // "어떤 잡을 fire 할지" 연결
                .withSchedule(CronScheduleBuilder.cronSchedule(CRON)
                        .inTimeZone(TZ_SEOUL)    // KST 강제 (UTC 노드 우회)
                        .withMisfireHandlingInstructionFireAndProceed())  // 누락 시 즉시 1회 fire 후 다음 정상 시각
                .build();
    }
}

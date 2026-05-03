package com.peoplecore.attendance.scheduler;

import com.peoplecore.attendance.entity.WorkGroup;
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
import java.util.TimeZone;

/*
 * EKS 배포 전 운영 체크리스트 (TODO).
 * Phase 1 Quartz JDBC 클러스터링 마이그레이션 완료 후, 운영 배포 직전 적용 대상.
 *
 * TODO-1 (배포 인프라): HPA minReplicas >= 1 보장 또는 배치 전용 Deployment 분리.
 *   왜: cron 시각에 파드 0대면 잡 fire 자체가 안 됨 (Quartz 는 살아있는 노드 중 한 대가 fire).
 *       야간 잡(자동마감 등)은 트래픽 없는 시각에 도는데 HPA 스케일다운으로 노드 0대 될 수 있음.
 *
 * TODO-2 (셧다운): Graceful Shutdown 보장.
 *   왜: SIGTERM 즉시 종료 시 fire 중인 잡 트랜잭션이 끊겨 부분 처리. 자동마감 등 멱등 보장 안 되는 잡은 데이터 정합성 깨짐.
 *   어떻게: application.yml 에 spring.lifecycle.timeout-per-shutdown-phase=60s,
 *           Pod spec 에 terminationGracePeriodSeconds=90.
 *           spring-boot-starter-quartz 가 종료 시 Scheduler.shutdown(true) 자동 호출.
 *
 * TODO-3 (DB 풀): HikariCP maximum-pool-size +2~5 증가.
 *   왜: Quartz 클러스터링은 QRTZ_LOCKS 테이블에 SELECT FOR UPDATE 를 주기적으로 발사 (clusterCheckinInterval 마다).
 *       기존 풀 그대로면 트래픽 피크 시 락 획득 대기로 잡 실행 지연.
 *
 * TODO-4 (시간대): 모든 CronTrigger 에 Asia/Seoul 명시.
 *   왜: EKS 노드 기본 timezone 은 보통 UTC. 명시 누락하면 cron 식이 UTC 로 해석돼 9시간 어긋남.
 *       ex: 23:00 자동마감이 한국 시각 익일 08:00 fire.
 *   어떻게: Quartz 트리거 빌드 시 .inTimeZone(TimeZone.getTimeZone("Asia/Seoul")).
 *
 * TODO-5 (시계 동기화): 노드 간 시계 차이 < 1초 (Quartz 권장 < 7초).
 *   왜: 클러스터 노드는 자기 시계 기준으로 fire 판단. 시계 차 크면 이중 fire 또는 misfire 오인.
 *   어떻게: AWS EC2/EKS 워커는 Amazon Time Sync Service 자동 사용 → 보통 OK.
 *           운영 시작 후 각 파드에서 date 출력 비교로 점검.
 *
 * TODO-6 (DB 마이그레이션): 배포 직전 RDS 에 메타테이블 수동 적용.
 *   왜: 운영 yml 의 initialize-schema=never 로 자동 생성 안 됨. 테이블 없으면 부팅 실패.
 *   어떻게: hr-service/src/main/resources/db/migration/ 에 두 SQL 커밋 후 RDS 에 실행:
 *           - quartz_tables_mysql_innodb.sql (Quartz jar 의 tables_mysql_innodb.sql)
 *           - spring_batch_schema_mysql.sql  (spring-batch-core jar 의 schema-mysql.sql)
 *
 * TODO-7 (실패 알림): JobListener 등록해서 잡 실패 시 Discord webhook 발사.
 *   왜: EKS 파드 로그 보존 짧음. 실패 인지 늦으면 자동마감/연차만료 등 하루치 데이터 누락이 그대로 굳음.
 *   어떻게: application-local.yml 의 discord.batch-webhook 재활용. JobListener 구현 추가.
 */

/*
 * 자동마감 배치의 근무그룹별 동적 스케줄러 관리자 (Quartz JDBC 클러스터링).
 *
 * 동작:
 *  - WorkGroup 하나당 JobDetail + CronTrigger 1쌍 등록 → QRTZ_JOB_DETAILS / QRTZ_TRIGGERS DB 영속.
 *  - fire 시각 = groupStartTime - 2h (KST 매일 1회). 예: 출근 09:00 → 07:00 KST 매일 fire.
 *    -2h 는 출근 직전 어제 데이터(미체크아웃/결근) 정리 + 알림 + 운영자 인지 여유분.
 *  - WorkGroup CRUD 시 WorkGroupService 에서 register / unregister 훅 호출.
 *  - 앱 기동 시 AutoCloseStartupLoader 가 활성 그룹 전부 register (멱등 — 동일 cron 이면 skip).
 *
 * 멱등성:
 *  - register 는 호출 시 트리거 존재 여부 확인.
 *      미존재 → JobDetail + Trigger INSERT
 *      존재 + cron 동일 → skip (DB 부하 0)
 *      존재 + cron 변경 → rescheduleJob (트리거만 갱신)
 *  - unregister 는 deleteJob — JobDetail + 연관 Trigger 모두 삭제. 미존재 시 noop.
 *
 * 클러스터링:
 *  - 멀티 인스턴스 fire 차단은 Quartz 의 QRTZ_LOCKS row lock 이 보장.
 *    트리거당 한 노드만 fire (synchronized / 인메모리 핸들 불필요).
 *  - WorkGroup CRUD 가 한 파드에서만 register/unregister 호출되어도 DB 영속이라
 *    다른 파드들은 다음 트리거 폴링 주기에 자동 동기화.
 *
 * 시간대:
 *  - 모든 cron 식이 Asia/Seoul 기준으로 평가됨 (TZ_SEOUL 상수 + CronScheduleBuilder.inTimeZone).
 *    EKS 노드/컨테이너 기본 timezone(UTC) 과 무관 — 누락 시 9시간 어긋남 (예: 07:00 cron 이 KST 16:00 fire).
 *
 * Misfire 정책:
 *  - DO_NOTHING — 자동마감/결근은 멱등 보장 안 됨.
 *    fire 누락 시 다음 정상 시각까지 대기. 운영자가 알림으로 인지 후 수동 트리거로 복구.
 */
@Component
@Slf4j
public class AutoCloseSchedulerManager {

    /* JobDetail / Trigger 식별용 그룹명 — Quartz Key 의 group 부분 */
    private static final String JOB_GROUP = "auto-close";
    /* JobDataMap 키 — AutoCloseJob 의 KEY_WORK_GROUP_ID / KEY_COMPANY_ID 와 일치해야 함 */
    private static final String KEY_WORK_GROUP_ID = "workGroupId";
    /* companyId 는 Spring Batch JobParameters 로 전파 → BatchFailureListener Discord 라벨 생성용 */
    private static final String KEY_COMPANY_ID = "companyId";
    /* 모든 트리거 cron 평가 기준 타임존 (EKS UTC 기본값 우회) */
    private static final TimeZone TZ_SEOUL = TimeZone.getTimeZone("Asia/Seoul");

    private final Scheduler scheduler;

    @Autowired
    public AutoCloseSchedulerManager(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /*
     * 근무그룹에 대해 자동마감 cron 등록 (멱등).
     *
     * 호출 시점: WorkGroup 신규 생성 / 시간 변경 / 앱 기동 시 StartupLoader.
     *
     * 처리 분기:
     *  - wg.groupDeleteAt != null → unregister 만 수행 (soft delete)
     *  - wg.groupStartTime null → 등록 스킵 + WARN 로그 (잘못된 데이터)
     *  - 트리거 미존재 → JobDetail + CronTrigger INSERT
     *  - 트리거 존재 + cron 동일 → skip
     *  - 트리거 존재 + cron 변경 → rescheduleJob
     *
     * 예외:
     *  - SchedulerException → ERROR 로그 + 흡수. 한 그룹 실패가 다른 그룹 처리를 막지 않도록.
     *  - ObjectAlreadyExistsException → 멀티 인스턴스 동시 부팅 race. INFO 로그 + 흡수 (다른 노드가 먼저 INSERT).
     */
    public void register(WorkGroup wg) {
        if (wg == null) return;
        Long workGroupId = wg.getWorkGroupId();

        if (wg.getGroupDeleteAt() != null) {
            unregister(workGroupId);
            return;
        }

        if (wg.getGroupStartTime() == null) {
            log.warn("[AutoCloseScheduler] groupStartTime null — 등록 스킵. workGroupId={}", workGroupId);
            return;
        }

        String cron = toCronExpression(wg.getGroupStartTime());
        JobKey jobKey = jobKeyOf(workGroupId);
        TriggerKey triggerKey = triggerKeyOf(workGroupId);

        try {
            // 트리거 존재 시 cron 비교 — 동일하면 skip, 다르면 reschedule
            if (scheduler.checkExists(triggerKey)) {
                CronTrigger existing = (CronTrigger) scheduler.getTrigger(triggerKey);
                if (existing != null && cron.equals(existing.getCronExpression())) {
                    log.debug("[AutoCloseScheduler] 동일 cron — skip. workGroupId={}, cron={}", workGroupId, cron);
                    return;
                }
                CronTrigger newTrigger = buildTrigger(triggerKey, jobKey, cron);
                scheduler.rescheduleJob(triggerKey, newTrigger);
                log.info("[AutoCloseScheduler] 재등록 — workGroupId={}, groupName={}, cron={}",
                        workGroupId, wg.getGroupName(), cron);
                return;
            }

            // 신규 등록 — addJob(replace=true) 로 잡 정의 멱등 보장 후 트리거 INSERT
            JobDetail jobDetail = JobBuilder.newJob(AutoCloseJob.class)
                    .withIdentity(jobKey)
                    .usingJobData(KEY_WORK_GROUP_ID, workGroupId)
                    .usingJobData(KEY_COMPANY_ID, wg.getCompany().getCompanyId().toString())
                    .storeDurably()  // 트리거 없이도 잡 정의 유지 (재등록 안전성)
                    .build();
            scheduler.addJob(jobDetail, true);
            scheduler.scheduleJob(buildTrigger(triggerKey, jobKey, cron));
            log.info("[AutoCloseScheduler] 신규 등록 — workGroupId={}, groupName={}, cron={}",
                    workGroupId, wg.getGroupName(), cron);
        } catch (ObjectAlreadyExistsException race) {
            // 멀티 인스턴스 동시 부팅 시 다른 노드가 먼저 INSERT — 정상 흐름.
            log.info("[AutoCloseScheduler] 등록 race 감지 — 다른 노드 선등록. workGroupId={}", workGroupId);
        } catch (SchedulerException e) {
            log.error("[AutoCloseScheduler] 등록 실패 — workGroupId={}", workGroupId, e);
        }
    }

    /*
     * 근무그룹 cron 해제 (DB 영속 삭제).
     * deleteJob 은 JobDetail + 연관 트리거 모두 삭제 — 미존재 시 false 반환 noop.
     * soft delete / 비활성화 시 호출.
     */
    public void unregister(Long workGroupId) {
        if (workGroupId == null) return;
        try {
            JobKey jobKey = jobKeyOf(workGroupId);
            if (scheduler.deleteJob(jobKey)) {
                log.info("[AutoCloseScheduler] 해제 — workGroupId={}", workGroupId);
            }
        } catch (SchedulerException e) {
            log.error("[AutoCloseScheduler] 해제 실패 — workGroupId={}", workGroupId, e);
        }
    }

    /*
     * 현재 등록된 자동마감 잡 수 — 디버그/테스트/운영 모니터링용.
     * QRTZ_JOB_DETAILS 의 JOB_GROUP='auto-close' 행 카운트.
     * 조회 실패 시 -1 반환 (예외 흡수, 호출부에서 음수 분기 가능).
     */
    public int activeCount() {
        try {
            return scheduler.getJobKeys(GroupMatcher.jobGroupEquals(JOB_GROUP)).size();
        } catch (SchedulerException e) {
            log.error("[AutoCloseScheduler] activeCount 조회 실패", e);
            return -1;
        }
    }

    /*
     * 근무그룹 시작시각 -2h 를 Quartz cron 표현식으로 변환.
     * Quartz cron 은 6필드 (초 분 시 일 월 요일) — 일과 요일 둘 중 하나는 ? 여야 함.
     * 매일 fire 라 일=*, 요일=? 로 고정.
     *
     * 예: 09:00 → fireAt 07:00 → "0 0 7 * * ?"  (KST 매일 07:00)
     *     01:00 → fireAt 23:00 → "0 0 23 * * ?" (전날 KST 23:00 으로 wrap)
     */
    static String toCronExpression(LocalTime startTime) {
        LocalTime fireAt = startTime.minusHours(2);
        return String.format("0 %d %d * * ?", fireAt.getMinute(), fireAt.getHour());
    }

    /* CronTrigger 빌더 — TZ + misfire 정책 일관 적용 */
    private CronTrigger buildTrigger(TriggerKey triggerKey, JobKey jobKey, String cron) {
        return TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobKey)
                .withSchedule(CronScheduleBuilder.cronSchedule(cron)
                        .inTimeZone(TZ_SEOUL)
                        .withMisfireHandlingInstructionDoNothing())
                .build();
    }

    private static JobKey jobKeyOf(Long workGroupId) {
        return JobKey.jobKey("wg-" + workGroupId, JOB_GROUP);
    }

    private static TriggerKey triggerKeyOf(Long workGroupId) {
        return TriggerKey.triggerKey("wg-" + workGroupId, JOB_GROUP);
    }
}

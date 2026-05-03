package com.peoplecore.attendance.controller;

import com.peoplecore.attendance.scheduler.PartitionSchedulerConfig;
import com.peoplecore.auth.RoleRequired;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/* 근태 도메인 운영 잡 수동 트리거 컨트롤러 */
/* 정기 fire 외 즉시 실행 시나리오: 장애 복구 / 정책 변경 즉시 반영 */
/* Quartz triggerJob 호출 → 멀티노드 한 대만 실행 보장 + 백그라운드 워커 스레드 처리 */
/* 권한: HR_SUPER_ADMIN 단독 (운영 잡 수동 트리거는 최고 권한자만) */
@Slf4j
@RestController
@RequestMapping("/admin/attendance")
public class AdminAttendanceJobController {

    /* JOB_GROUP — AutoCloseSchedulerManager.JOB_GROUP 상수와 일치해야 함 */
    private static final String AUTO_CLOSE_GROUP = "auto-close";

    private final Scheduler quartzScheduler;

    @Autowired
    public AdminAttendanceJobController(Scheduler quartzScheduler) {
        this.quartzScheduler = quartzScheduler;
    }

    /* 파티션 사전 생성 즉시 실행 */
    @RoleRequired({"HR_SUPER_ADMIN"})
    @PostMapping("/partition/ensure")
    public ResponseEntity<Void> ensurePartition(@RequestHeader("X-User-Id") String userId) {
        return triggerOrFail(
                JobKey.jobKey(PartitionSchedulerConfig.JOB_NAME, PartitionSchedulerConfig.JOB_GROUP),
                userId);
    }

    /*
     * 자동마감 + 결근 처리 즉시 실행 (WorkGroup 단위).
     *
     * 호출 경로:
     *  - quartzScheduler.triggerJob(jobKey) → AutoCloseJob.execute()
     *    → JobLauncher.run(autoCloseJob, params(companyId, workGroupId, targetDate))
     *    → autoCloseStep → absentStep
     *  - JobInstance(companyId, workGroupId, targetDate) UNIQUE — 같은 WorkGroup 같은 날 중복 차단
     *
     * jobKey 형식: AutoCloseSchedulerManager.jobKeyOf 와 동일 ("wg-{workGroupId}", "auto-close")
     * → 미등록 WorkGroup 호출 시 SchedulerException → 500 반환
     */
    @RoleRequired({"HR_SUPER_ADMIN"})
    @PostMapping("/auto-close/{workGroupId}/run")
    public ResponseEntity<Void> runAutoClose(@PathVariable Long workGroupId,
                                             @RequestHeader("X-User-Id") String userId) {
        return triggerOrFail(JobKey.jobKey("wg-" + workGroupId, AUTO_CLOSE_GROUP), userId);
    }

    /* Quartz Job 즉시 트리거 — 응답은 즉시(202), 실행은 워커 스레드에서 */
    private ResponseEntity<Void> triggerOrFail(JobKey jobKey, String userId) {
        try {
            quartzScheduler.triggerJob(jobKey);
            log.info("[AdminJob] {} 수동 트리거 - userId={}", jobKey, userId);
            return ResponseEntity.accepted().build();
        } catch (SchedulerException e) {
            log.error("[AdminJob] {} 트리거 실패 - userId={}", jobKey, userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

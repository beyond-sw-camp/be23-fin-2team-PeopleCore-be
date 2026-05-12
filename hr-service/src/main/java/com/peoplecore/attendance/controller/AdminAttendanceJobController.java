package com.peoplecore.attendance.controller;

import com.peoplecore.attendance.batch.AutoCloseJobConfig;
import com.peoplecore.attendance.batch.BatchConfig;
import com.peoplecore.attendance.entity.WorkGroup;
import com.peoplecore.attendance.repository.WorkGroupRepository;
import com.peoplecore.attendance.scheduler.PartitionSchedulerConfig;
import com.peoplecore.auth.RoleRequired;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;

/* 근태 도메인 운영 잡 수동 트리거 컨트롤러 */
/* 정기 fire 외 즉시 실행 시나리오: 장애 복구 / 정책 변경 즉시 반영 */
/* 권한: HR_SUPER_ADMIN 단독 (운영 잡 수동 트리거는 최고 권한자만) */
@Slf4j
@RestController
@RequestMapping("/admin/attendance")
public class AdminAttendanceJobController {

    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final Scheduler quartzScheduler;
    private final JobLauncher jobLauncher;
    private final org.springframework.batch.core.Job autoCloseJob;
    private final WorkGroupRepository workGroupRepository;

    @Autowired
    public AdminAttendanceJobController(Scheduler quartzScheduler,
                                        @Qualifier(BatchConfig.AUTO_CLOSE_JOB_LAUNCHER) JobLauncher jobLauncher,
                                        @Qualifier(AutoCloseJobConfig.JOB_NAME) org.springframework.batch.core.Job autoCloseJob,
                                        WorkGroupRepository workGroupRepository) {
        this.quartzScheduler = quartzScheduler;
        this.jobLauncher = jobLauncher;
        this.autoCloseJob = autoCloseJob;
        this.workGroupRepository = workGroupRepository;
    }

    /* 파티션 사전 생성 즉시 실행 — 단일 잡이라 Quartz triggerJob 유지 */
    @RoleRequired({"HR_SUPER_ADMIN"})
    @PostMapping("/partition/ensure")
    public ResponseEntity<Void> ensurePartition(@RequestHeader("X-User-Id") String userId) {
        JobKey jobKey = JobKey.jobKey(PartitionSchedulerConfig.JOB_NAME, PartitionSchedulerConfig.JOB_GROUP);
        try {
            quartzScheduler.triggerJob(jobKey);
            log.info("[AdminJob] {} 수동 트리거 - userId={}", jobKey, userId);
            return ResponseEntity.accepted().build();
        } catch (SchedulerException e) {
            log.error("[AdminJob] {} 트리거 실패 - userId={}", jobKey, userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /* 자동마감 수동 실행 — JobLauncher 직접 호출로 @DisallowConcurrentExecution 락 우회.
       비동기 launcher 라 INSERT 끝나면 즉시 202 응답, Step 은 풀에서 처리 */
    @RoleRequired({"HR_SUPER_ADMIN"})
    @PostMapping("/auto-close/{workGroupId}/run")
    public ResponseEntity<Void> runAutoClose(@PathVariable Long workGroupId,
                                             @RequestHeader("X-User-Id") String userId) {
        WorkGroup wg = workGroupRepository.findByWorkGroupIdAndGroupDeleteAtIsNull(workGroupId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORK_GROUP_NOT_FOUND));

        LocalDate targetDate = LocalDate.now(ZONE_SEOUL).minusDays(1);
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("companyId", wg.getCompany().getCompanyId().toString())
                    .addLong("workGroupId", workGroupId)
                    .addString("targetDate", targetDate.toString())
                    .toJobParameters();
            jobLauncher.run(autoCloseJob, params);
            log.info("[AdminJob] auto-close 수동 실행 - workGroupId={}, date={}, userId={}",
                    workGroupId, targetDate, userId);
            return ResponseEntity.accepted().build();
        } catch (Exception e) {
            log.error("[AdminJob] auto-close 수동 실행 실패 - workGroupId={}, userId={}",
                    workGroupId, userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

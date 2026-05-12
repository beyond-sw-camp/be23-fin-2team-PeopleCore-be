package com.peoplecore.attendance.scheduler;

import com.peoplecore.attendance.batch.AutoCloseJobConfig;
import com.peoplecore.attendance.batch.BatchConfig;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.LocalDate;
import java.time.ZoneId;

/*
 * 단일 JobDetail + wg 별 Trigger 패턴의 자동마감 Job.
 * @DisallowConcurrentExecution + 비동기 JobLauncher 조합으로
 * BATCH_JOB_INSTANCE INSERT 직렬화 / Step 풀 병렬 실행.
 */
@Slf4j
@DisallowConcurrentExecution
public class AutoCloseJob implements Job {

    private static final String KEY_WORK_GROUP_ID = "workGroupId";
    private static final String KEY_COMPANY_ID = "companyId";
    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired
    @Qualifier(BatchConfig.AUTO_CLOSE_JOB_LAUNCHER)
    private JobLauncher jobLauncher;  // 비동기 launcher — INSERT 동기, Step 풀 위임

    @Autowired
    @Qualifier(AutoCloseJobConfig.JOB_NAME)
    private org.springframework.batch.core.Job autoCloseJob;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Long workGroupId = context.getMergedJobDataMap().getLong(KEY_WORK_GROUP_ID);
        String companyId = context.getMergedJobDataMap().getString(KEY_COMPANY_ID);
        LocalDate targetDate = LocalDate.now(ZONE_SEOUL).minusDays(1);  // 어제 KST

        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("companyId", companyId)
                    .addLong("workGroupId", workGroupId)
                    .addString("targetDate", targetDate.toString())  // (companyId, wgId, targetDate) UNIQUE
                    .toJobParameters();
            jobLauncher.run(autoCloseJob, params);
        } catch (JobInstanceAlreadyCompleteException e) {
            // 운영자 수동 재실행 시 정상 흐름
            log.info("[AutoCloseJob] 이미 완료 - workGroupId={}, date={}", workGroupId, targetDate);
        } catch (Exception e) {
            log.error("[AutoCloseJob] Batch 실행 실패 - workGroupId={}, date={}", workGroupId, targetDate, e);
            throw new JobExecutionException(e, false);  // JobFailureNotifier 가 Discord 알림
        }
    }
}

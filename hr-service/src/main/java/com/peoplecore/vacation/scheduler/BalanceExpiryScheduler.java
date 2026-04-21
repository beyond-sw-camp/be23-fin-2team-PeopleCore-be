package com.peoplecore.vacation.scheduler;

import com.peoplecore.vacation.batch.BalanceExpiryJobConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/* 만료 Batch 런처 - BATCH_JOB_INSTANCE 유니크로 중복 실행 자동 차단 (Redis 락 불필요) */
/* 매일 00:20 KST - 발생/통지 잡 종료 후 마지막에 만료 처리 */
@Component
@Slf4j
public class BalanceExpiryScheduler {

    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final JobLauncher jobLauncher;
    private final Job balanceExpiryJob;

    @Autowired
    public BalanceExpiryScheduler(JobLauncher jobLauncher,
                                  @Qualifier(BalanceExpiryJobConfig.JOB_NAME) Job balanceExpiryJob) {
        this.jobLauncher = jobLauncher;
        this.balanceExpiryJob = balanceExpiryJob;
    }

    @Scheduled(cron = "0 20 0 * * *", zone = "Asia/Seoul")
    public void run() {
        LocalDate today = LocalDate.now(ZONE_SEOUL);
        try {
            // targetDate 를 식별 파라미터로 써서 같은 날 중복 실행 자동 차단
            JobParameters params = new JobParametersBuilder()
                    .addString("targetDate", today.toString())
                    .toJobParameters();
            JobExecution exec = jobLauncher.run(balanceExpiryJob, params);

            StepExecution step = exec.getStepExecutions().stream().findFirst().orElse(null);
            long read = step != null ? step.getReadCount() : 0;
            long write = step != null ? step.getWriteCount() : 0;
            log.info("[BalanceExpiryBatch] 완료 - date={}, status={}, read={}, write={}",
                    today, exec.getStatus(), read, write);
        } catch (JobInstanceAlreadyCompleteException e) {
            log.info("[BalanceExpiryBatch] 오늘 이미 완료됨 - skip. date={}", today);
        } catch (Exception e) {
            log.error("[BalanceExpiryBatch] 실행 실패 - date={}, err={}", today, e.getMessage(), e);
        }
    }
}

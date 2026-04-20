package com.peoplecore.vacation.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/* 배치 Job 실패 감지 리스너 - 모든 vacation 배치 Job 에 공통 attach */
/* 성공: INFO 요약. 실패: ERROR 로그 + Discord 웹훅 알림 */
@Component
@Slf4j
public class BatchFailureListener implements JobExecutionListener {

    private final DiscordNotifier discordNotifier;

    @Autowired
    public BatchFailureListener(DiscordNotifier discordNotifier) {
        this.discordNotifier = discordNotifier;
    }

    /* 실패 / ABANDONED / STOPPED 감지 - 성공이면 info 한 줄만 */
    @Override
    public void afterJob(JobExecution jobExecution) {
        BatchStatus status = jobExecution.getStatus();
        String jobName = jobExecution.getJobInstance().getJobName();

        if (status == BatchStatus.COMPLETED) {
            StepExecution step = jobExecution.getStepExecutions().stream().findFirst().orElse(null);
            long readCount = step != null ? step.getReadCount() : 0;
            long writeCount = step != null ? step.getWriteCount() : 0;
            long skipCount = step != null ? step.getSkipCount() : 0;

            log.info("[BatchOK] job={}, params={}, read={}, write={}, skip={}",
                    jobName, jobExecution.getJobParameters(), readCount, writeCount, skipCount);

            // 부분 실패 경고 - Step 은 성공했지만 skip 된 item 이 있는 경우 노란색 알림
            if (skipCount > 0) {
                try {
                    discordNotifier.notifyBatchWarning(
                            jobName,
                            jobExecution.getJobParameters().toString(),
                            readCount, writeCount, skipCount);
                } catch (Exception e) {
                    log.warn("[BatchFailureListener] Discord 경고 알림 트리거 중 예외 - job={}, err={}",
                            jobName, e.getMessage());
                }
            }
            return;
        }

        // 실패 경로 - 모든 실패 타입 공통 처리
        List<Throwable> failures = jobExecution.getAllFailureExceptions();
        Throwable rootCause = failures.isEmpty() ? null : failures.get(0);
        String rootCauseMsg = rootCause != null
                ? rootCause.getClass().getSimpleName() + ": " + rootCause.getMessage()
                : "N/A";
        String exitCode = jobExecution.getExitStatus().getExitCode();
        String params = jobExecution.getJobParameters().toString();

        log.error("[BatchFAIL] job={}, status={}, params={}, exitCode={}, failures={}, rootCause={}",
                jobName, status, params, exitCode, failures.size(), rootCauseMsg, rootCause);

        // Discord 웹훅 전송 - 비동기 fire-and-forget, 실패해도 배치 상태 영향 없음
        try {
            discordNotifier.notifyBatchFailure(jobName, params, exitCode, failures.size(), rootCauseMsg);
        } catch (Exception e) {
            log.warn("[BatchFailureListener] Discord 알림 트리거 중 예외 - job={}, err={}", jobName, e.getMessage());
        }
    }
}

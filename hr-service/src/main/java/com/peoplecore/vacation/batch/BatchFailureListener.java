package com.peoplecore.vacation.batch;

import com.peoplecore.company.repository.CompanyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/* 배치 Job 실패 감지 리스너 - 모든 vacation 배치 Job 에 공통 attach */
/* 성공: INFO 요약. 실패: ERROR 로그 + Discord 웹훅 알림. 부분실패(skipCount>0): WARN 알림 */
@Component
@Slf4j
public class BatchFailureListener implements JobExecutionListener {

    private final DiscordNotifier discordNotifier;
    private final CompanyRepository companyRepository;

    @Autowired
    public BatchFailureListener(DiscordNotifier discordNotifier,
                                CompanyRepository companyRepository) {
        this.discordNotifier = discordNotifier;
        this.companyRepository = companyRepository;
    }

    /* 실패 / ABANDONED / STOPPED 감지 + 부분실패 분기 */
    @Override
    public void afterJob(JobExecution jobExecution) {
        BatchStatus status = jobExecution.getStatus();
        String jobName = jobExecution.getJobInstance().getJobName();
        JobParameters params = jobExecution.getJobParameters();
        String companyLabel = resolveCompanyLabel(params); // Discord embed 용 회사 라벨

        if (status == BatchStatus.COMPLETED) {
            StepExecution step = jobExecution.getStepExecutions().stream().findFirst().orElse(null);
            long readCount = step != null ? step.getReadCount() : 0;
            long writeCount = step != null ? step.getWriteCount() : 0;
            long skipCount = step != null ? step.getSkipCount() : 0;

            log.info("[BatchOK] job={}, company={}, params={}, read={}, write={}, skip={}",
                    jobName, companyLabel, params, readCount, writeCount, skipCount);

            // 부분 실패 경고 - Step 은 성공했지만 skip 된 item 이 있는 경우 노란색 알림
            if (skipCount > 0) {
                try {
                    discordNotifier.notifyBatchWarning(
                            jobName, companyLabel, params.toString(),
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

        log.error("[BatchFAIL] job={}, company={}, status={}, params={}, exitCode={}, failures={}, rootCause={}",
                jobName, companyLabel, status, params, exitCode, failures.size(), rootCauseMsg, rootCause);

        // Discord 웹훅 전송 - 비동기 fire-and-forget, 실패해도 배치 상태 영향 없음
        try {
            discordNotifier.notifyBatchFailure(
                    jobName, companyLabel, params.toString(),
                    exitCode, failures.size(), rootCauseMsg);
        } catch (Exception e) {
            log.warn("[BatchFailureListener] Discord 알림 트리거 중 예외 - job={}, err={}", jobName, e.getMessage());
        }
    }

    /* JobParameters 에서 companyId 꺼내 회사명 조회 → "{companyName} ({uuid앞8자리})" 형태 */
    /* companyId 없는 전사 통합 잡(balanceExpiryJob) → "전사 통합" */
    /* UUID 파싱 실패/회사 없음 → "UNKNOWN ({원문})" 로그로 디버깅 가능하게 */
    private String resolveCompanyLabel(JobParameters params) {
        String companyIdStr = params.getString("companyId");
        if (companyIdStr == null || companyIdStr.isBlank()) {
            return "전사 통합";
        }
        try {
            UUID companyId = UUID.fromString(companyIdStr);
            return companyRepository.findById(companyId)
                    .map(c -> c.getCompanyName() + " (" + companyIdStr.substring(0, 8) + ")")
                    .orElse("UNKNOWN (" + companyIdStr + ")");
        } catch (IllegalArgumentException e) {
            // UUID 포맷 아님 - 라벨 조회 실패해도 Discord 알림은 계속 나가야 함
            return "UNKNOWN (" + companyIdStr + ")";
        }
    }
}

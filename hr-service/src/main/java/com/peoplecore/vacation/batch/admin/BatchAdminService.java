package com.peoplecore.vacation.batch.admin;

import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.batch.DiscordNotifier;
import com.peoplecore.vacation.batch.admin.dto.BatchExecutionResponse;
import com.peoplecore.vacation.batch.admin.dto.BatchRerunRequest;
import com.peoplecore.vacation.batch.admin.dto.BatchRerunResponse;
import com.peoplecore.vacation.batch.admin.dto.DiscordTestRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/* 배치 실행 이력 조회 + 수동 재실행 서비스 (회사 분기 검증 포함) */
@Service
@Slf4j
public class BatchAdminService {

    /* 재실행 허용 Job 화이트리스트 */
    private static final Set<String> SUPPORTED_JOBS =
            Set.of("balanceExpiryJob", "annualGrantFiscalJob", "promotionNoticeJob");

    /* 전사 통합 Job - HR_SUPER_ADMIN 재실행 금지, 조회 목록에서도 제외 */
    private static final String GLOBAL_JOB_BALANCE_EXPIRY = "balanceExpiryJob";

    /* 조회 limit 상한 */
    private static final int MAX_LIMIT = 200;

    private final JobExplorer jobExplorer;
    private final JobLauncher jobLauncher;
    private final ApplicationContext applicationContext; // Job 빈 동적 조회
    private final DiscordNotifier discordNotifier;       // 수동 알림 테스트용

    @Autowired
    public BatchAdminService(JobExplorer jobExplorer,
                             JobLauncher jobLauncher,
                             ApplicationContext applicationContext,
                             DiscordNotifier discordNotifier) {
        this.jobExplorer = jobExplorer;
        this.jobLauncher = jobLauncher;
        this.applicationContext = applicationContext;
        this.discordNotifier = discordNotifier;
    }

    /* Discord 알림 단독 테스트 - 실제 배치를 돌리지 않고 DiscordNotifier 만 호출 */
    /* null 필드는 기본값으로 채움. rootCauseMessage 길게 넣으면 truncate(900) 동작 확인 가능 */
    public void sendTestDiscordAlert(DiscordTestRequest req) {
        String jobName = (req.getJobName() != null && !req.getJobName().isBlank())
                ? req.getJobName() : "testJob";
        String params = (req.getParams() != null && !req.getParams().isBlank())
                ? req.getParams()
                : "{companyId=00000000-0000-0000-0000-000000000000, targetDate=2026-04-20, stage=FIRST}";
        String exitCode = (req.getExitCode() != null && !req.getExitCode().isBlank())
                ? req.getExitCode() : "FAILED";
        int failureCount = (req.getFailureCount() != null) ? req.getFailureCount() : 1;
        String rootCause = (req.getRootCauseMessage() != null && !req.getRootCauseMessage().isBlank())
                ? req.getRootCauseMessage()
                : "RuntimeException: Discord 알림 테스트 메시지";
        String companyLabel = "테스트회사 (00000000)"; // 테스트 라벨 고정

        discordNotifier.notifyBatchFailure(jobName, companyLabel, params, exitCode, failureCount, rootCause);
    }

    /* 최근 실행 이력 조회 - 본인 회사 JobParameters 와 일치하는 이력만. 전사 통합 Job 은 제외 */
    /* callerCompanyId 가 null 이면 BATCH_TENANT_FORBIDDEN (헤더 누락 방어) */
    public List<BatchExecutionResponse> listRecent(String jobName, int limit, UUID callerCompanyId) {
        if (callerCompanyId == null) {
            throw new CustomException(ErrorCode.BATCH_TENANT_FORBIDDEN);
        }
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));

        // 특정 Job 지정이 전사 통합 Job 이면 빈 목록 반환 (조회 권한 없음)
        if (GLOBAL_JOB_BALANCE_EXPIRY.equals(jobName)) {
            return List.of();
        }

        // 전체 통합 조회 시 balanceExpiryJob 제외
        Collection<String> targets = (jobName != null && !jobName.isBlank())
                ? List.of(jobName)
                : SUPPORTED_JOBS.stream().filter(j -> !GLOBAL_JOB_BALANCE_EXPIRY.equals(j)).toList();

        List<BatchExecutionResponse> buffer = new ArrayList<>();
        for (String name : targets) {
            List<JobInstance> instances = jobExplorer.getJobInstances(name, 0, safeLimit);
            for (JobInstance instance : instances) {
                for (JobExecution exec : jobExplorer.getJobExecutions(instance)) {
                    // 본인 회사 JobParameters 와 일치하는 것만 통과
                    if (belongsToCaller(exec, callerCompanyId)) {
                        buffer.add(toResponse(exec));
                    }
                }
            }
        }

        buffer.sort(Comparator.comparing(
                BatchExecutionResponse::getStartTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return buffer.size() > safeLimit ? buffer.subList(0, safeLimit) : buffer;
    }

    /* 재실행 트리거 - RESTART(기본) / FRESH. 본인 회사 건만 허용 */
    /* 예외: 화이트리스트 밖 jobName → BATCH_JOB_NOT_SUPPORTED */
    /* 예외: balanceExpiryJob (전사 통합) → BATCH_TENANT_FORBIDDEN */
    /* 예외: 헤더/바디 companyId 불일치 → BATCH_TENANT_FORBIDDEN */
    /* 예외: Job Bean 없음 → BATCH_JOB_NOT_FOUND */
    /* 예외: 필수 파라미터 누락 → BATCH_PARAMETER_INVALID */
    /* 예외: JobLauncher.run 도중 실패 → BATCH_RERUN_FAILED */
    public BatchRerunResponse rerun(String jobName, BatchRerunRequest request, UUID callerCompanyId) {
        if (!SUPPORTED_JOBS.contains(jobName)) {
            throw new CustomException(ErrorCode.BATCH_JOB_NOT_SUPPORTED);
        }
        // 전사 통합 Job 은 HR_SUPER_ADMIN 도 재실행 불가 (플랫폼 운영팀 전용)
        if (GLOBAL_JOB_BALANCE_EXPIRY.equals(jobName)) {
            throw new CustomException(ErrorCode.BATCH_TENANT_FORBIDDEN);
        }
        // 헤더 누락 방어 + 헤더 ↔ 바디 companyId 일치 검증
        if (callerCompanyId == null
                || request.getCompanyId() == null
                || !callerCompanyId.equals(request.getCompanyId())) {
            throw new CustomException(ErrorCode.BATCH_TENANT_FORBIDDEN);
        }

        Job job;
        try {
            job = applicationContext.getBean(jobName, Job.class);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.BATCH_JOB_NOT_FOUND);
        }

        BatchRerunRequest.RerunMode mode = request.getMode() == null
                ? BatchRerunRequest.RerunMode.RESTART
                : request.getMode();

        JobParametersBuilder builder = buildBaseParameters(jobName, request);
        String message = null;

        // RESTART 인데 동일 파라미터가 이미 COMPLETED 면 JobInstanceAlreadyCompleteException 발생
        // 관리자 의도 존중 - FRESH 로 승격하고 경고 메시지 동봉
        if (mode == BatchRerunRequest.RerunMode.RESTART
                && isAlreadyCompleted(jobName, builder.toJobParameters())) {
            mode = BatchRerunRequest.RerunMode.FRESH;
            message = "동일 파라미터가 이미 COMPLETED - FRESH 로 자동 전환";
        }

        if (mode == BatchRerunRequest.RerunMode.FRESH) {
            // tiebreaker - JobInstance 식별자 강제 분기
            builder.addLong("rerunAt", System.currentTimeMillis());
        }

        try {
            JobExecution exec = jobLauncher.run(job, builder.toJobParameters());
            return BatchRerunResponse.builder()
                    .executionId(exec.getId())
                    .status(exec.getStatus().name())
                    .appliedMode(mode.name())
                    .message(message)
                    .build();
        } catch (Exception e) {
            log.error("[BatchAdmin] 재실행 실패 - job={}, err={}", jobName, e.getMessage(), e);
            throw new CustomException(ErrorCode.BATCH_RERUN_FAILED);
        }
    }

    /* Job 별 필수 파라미터 조립 - 누락 시 BATCH_PARAMETER_INVALID */
    private JobParametersBuilder buildBaseParameters(String jobName, BatchRerunRequest req) {
        if (req.getTargetDate() == null) {
            throw new CustomException(ErrorCode.BATCH_PARAMETER_INVALID);
        }

        JobParametersBuilder b = new JobParametersBuilder();
        b.addString("targetDate", req.getTargetDate().toString()); // 모든 Job 공통

        switch (jobName) {
            case "annualGrantFiscalJob":
                // companyId 는 tenant 검증에서 이미 확인됨
                b.addString("companyId", req.getCompanyId().toString());
                break;

            case "promotionNoticeJob":
                if (req.getStage() == null || req.getMonthsBefore() == null) {
                    throw new CustomException(ErrorCode.BATCH_PARAMETER_INVALID);
                }
                b.addString("companyId", req.getCompanyId().toString());
                b.addString("stage", req.getStage());
                b.addLong("monthsBefore", req.getMonthsBefore());
                break;

            default:
                // balanceExpiryJob 은 rerun 진입점에서 이미 차단됨 → 여기 도달하면 화이트리스트 오류
                throw new CustomException(ErrorCode.BATCH_JOB_NOT_SUPPORTED);
        }
        return b;
    }

    /* JobExecution 의 JobParameters.companyId 가 호출자 companyId 와 일치하는지 */
    /* companyId 파라미터 없음(balanceExpiryJob 등) → false (조회 목록 제외) */
    private boolean belongsToCaller(JobExecution exec, UUID callerCompanyId) {
        String jobCompanyId = exec.getJobParameters().getString("companyId");
        if (jobCompanyId == null || jobCompanyId.isBlank()) {
            return false;
        }
        return callerCompanyId.toString().equals(jobCompanyId);
    }

    /* 동일 JobParameters 로 COMPLETED JobExecution 존재 여부 */
    private boolean isAlreadyCompleted(String jobName, JobParameters params) {
        JobInstance instance = jobExplorer.getJobInstance(jobName, params);
        if (instance == null) return false;
        return jobExplorer.getJobExecutions(instance).stream()
                .anyMatch(e -> e.getStatus() == BatchStatus.COMPLETED);
    }

    /* JobExecution → 응답 DTO. Spring Batch 5 는 getStartTime/getEndTime 이 LocalDateTime 반환 */
    private BatchExecutionResponse toResponse(JobExecution exec) {
        StepExecution step = exec.getStepExecutions().stream().findFirst().orElse(null);
        String rootCause = exec.getAllFailureExceptions().isEmpty()
                ? null
                : exec.getAllFailureExceptions().get(0).toString();

        return BatchExecutionResponse.builder()
                .executionId(exec.getId())
                .instanceId(exec.getJobInstance().getInstanceId())
                .jobName(exec.getJobInstance().getJobName())
                .status(exec.getStatus().name())
                .exitCode(exec.getExitStatus().getExitCode())
                .parameters(exec.getJobParameters().toString())
                .startTime(exec.getStartTime())
                .endTime(exec.getEndTime())
                .readCount(step != null ? step.getReadCount() : 0)
                .writeCount(step != null ? step.getWriteCount() : 0)
                .skipCount(step != null ? step.getSkipCount() : 0)
                .rootCauseMessage(rootCause)
                .build();
    }
}

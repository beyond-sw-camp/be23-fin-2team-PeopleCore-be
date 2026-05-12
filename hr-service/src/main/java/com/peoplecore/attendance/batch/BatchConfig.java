package com.peoplecore.attendance.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/*
 * 자동마감 전용 비동기 JobLauncher.
 * @DisallowConcurrentExecution 이 execute() 를 직렬화할 때 그 안의 jobLauncher.run() 을
 * INSERT 만 동기 / Step 풀 위임 으로 분리해 처리량 손해 없이 INSERT 만 직렬화.
 * vacation 도메인은 동기 종료 상태(JobExecution.status) 를 후속 분기에 쓰므로 빈 분리 (@Primary X).
 */
@Slf4j
@Configuration
public class BatchConfig {

    public static final String AUTO_CLOSE_JOB_LAUNCHER = "autoCloseJobLauncher";

    /* 자동마감 Step 실행 전용 풀 — 다른 도메인과 격리 */
    @Bean(name = "autoCloseTaskExecutor")
    public TaskExecutor autoCloseTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(200);  // 큐 가득 차면 caller 블록 → @DisallowConcurrentExecution 다음 fire 차단
        executor.setThreadNamePrefix("batch-auto-close-");
        executor.setWaitForTasksToCompleteOnShutdown(true);  // Graceful Shutdown
        executor.setAwaitTerminationSeconds(60);             // terminationGracePeriodSeconds=90 와 정합
        executor.initialize();
        return executor;
    }

    /* INSERT 는 호출 스레드(Quartz worker) 동기, Step 은 풀 위임 */
    @Bean(name = AUTO_CLOSE_JOB_LAUNCHER)
    public JobLauncher autoCloseJobLauncher(JobRepository jobRepository,
                                            TaskExecutor autoCloseTaskExecutor) throws Exception {
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(autoCloseTaskExecutor);
        launcher.afterPropertiesSet();
        return launcher;
    }
}

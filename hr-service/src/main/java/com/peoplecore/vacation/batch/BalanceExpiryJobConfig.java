package com.peoplecore.vacation.batch;

import com.peoplecore.vacation.entity.VacationBalance;
import com.peoplecore.vacation.service.BalanceExpiryService;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaCursorItemReader;
import org.springframework.batch.item.database.builder.JpaCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.Map;

/* 만료 처리 Batch Job - 일일 만료 balance 일괄 expire */
/* JobInstance 식별자: targetDate - 같은 날짜 재실행 시 JobInstanceAlreadyCompleteException 로 자동 dedup */
@Configuration
@Slf4j
public class BalanceExpiryJobConfig {

    /* 청크 단위 - 100건마다 외부 tx commit. expireBalance 는 REQUIRES_NEW 라 외부 tx 는 비어있음 */
    private static final int CHUNK_SIZE = 100;

    /* 잡 이름 - 스케줄러에서 @Qualifier 로 주입 */
    public static final String JOB_NAME = "balanceExpiryJob";

    @Bean(JOB_NAME)
    public Job balanceExpiryJob(JobRepository jobRepository, Step balanceExpiryStep,
                                BatchFailureListener batchFailureListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(batchFailureListener)
                .start(balanceExpiryStep)
                .build();
    }

    @Bean
    public Step balanceExpiryStep(JobRepository jobRepository,
                                  PlatformTransactionManager transactionManager,
                                  JpaCursorItemReader<VacationBalance> balanceExpiryReader,
                                  ItemWriter<VacationBalance> balanceExpiryWriter) {
        return new StepBuilder("balanceExpiryStep", jobRepository)
                .<VacationBalance, VacationBalance>chunk(CHUNK_SIZE, transactionManager)
                .reader(balanceExpiryReader)
                .writer(balanceExpiryWriter)
                .build();
    }

    /* 만료일 도래 balance 커서 조회 - JOIN FETCH 로 employee/vacationType 함께 로드 (N+1 방지) */
    /* 커서 리더는 페이지가 아니라 스트리밍이라 JOIN FETCH 안전 */
    @Bean
    @StepScope
    public JpaCursorItemReader<VacationBalance> balanceExpiryReader(
            EntityManagerFactory emf,
            @Value("#{jobParameters['targetDate']}") String targetDateStr) {
        LocalDate targetDate = LocalDate.parse(targetDateStr);
        return new JpaCursorItemReaderBuilder<VacationBalance>()
                .name("balanceExpiryReader")
                .entityManagerFactory(emf)
                .queryString("""
                        SELECT b FROM VacationBalance b
                        JOIN FETCH b.employee
                        JOIN FETCH b.vacationType
                        WHERE b.expiresAt IS NOT NULL
                          AND b.expiresAt <= :targetDate
                        """)
                .parameterValues(Map.of("targetDate", targetDate))
                .build();
    }

    /* 청크 writer - balance 단위 expireBalance 위임. 예외는 per-item 로그 (다음 item 계속 진행) */
    @Bean
    public ItemWriter<VacationBalance> balanceExpiryWriter(BalanceExpiryService balanceExpiryService) {
        return chunk -> {
            for (VacationBalance balance : chunk.getItems()) {
                try {
                    balanceExpiryService.expireBalance(balance);
                } catch (Exception e) {
                    // REQUIRES_NEW 로 격리되어 있으므로 catch 해도 외부 chunk tx 영향 없음
                    log.error("[BalanceExpiryBatch] balance 처리 실패 - balanceId={}, err={}",
                            balance.getBalanceId(), e.getMessage(), e);
                }
            }
        };
    }
}

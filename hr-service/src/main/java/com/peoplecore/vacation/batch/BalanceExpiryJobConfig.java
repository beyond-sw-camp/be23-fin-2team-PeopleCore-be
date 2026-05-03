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
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/* 만료 처리 Batch Job - 일일 만료 balance 일괄 expire */
/* JobInstance 식별자: targetDate - 같은 날짜 재실행 시 JobInstanceAlreadyCompleteException 로 자동 dedup */
@Configuration
@Slf4j
public class BalanceExpiryJobConfig {

    /* 청크 단위 - 100건마다 외부 tx commit. expireBalance 는 REQUIRES_NEW 라 외부 tx 는 비어있음 */
    private static final int CHUNK_SIZE = 100;

    /* 허용 skip 상한 - 초과 시 Step FAILED → Discord 빨간색 알림 (기존 경로) */
    private static final int SKIP_LIMIT = 50;

    /* 잡 이름 - 스케줄러에서 @Qualifier 로 주입 */
    public static final String JOB_NAME = "balanceExpiryJob";

    /*
     * TODO 고도화 예정 — Multi-thread Step (taskExecutor + throttleLimit) 도입 검토
     *  - 청크 병렬 처리로 처리 속도 향상
     *  - 전제 1: ItemReader thread-safety (JpaCursor 는 안전 X → SynchronizedItemStreamReader 래핑 필요)
     *  - 전제 2: ItemWriter → Service REQUIRES_NEW 격리 (이미 적용)
     *  - 대안: Partitioned Step (회사별 파티션 병렬)
     */
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
                .faultTolerant()
                .retry(TransientDataAccessException.class)   // DB 락/데드락/타임아웃 등 일시 장애 자동 복구
                .retryLimit(3)
                .skip(Exception.class)   // writer 에서 튀는 예외는 item 단위 skip
                .skipLimit(SKIP_LIMIT)   // 초과 시 SkipLimitExceededException → Step FAILED
                .build();
    }

    /* 만료일 도래 balance 커서 조회 - 회사별 필터 + JOIN FETCH (N+1 방지) */
    /* 커서 리더는 페이지가 아니라 스트리밍이라 JOIN FETCH 안전 */
    /* companyId 파라미터로 회사 단위 분리 - 다른 회사 데이터 섞임 방지 + 재처리 격리 */
    /* available(= total - used - pending - expired) > 0 조건으로 이미 처리된 balance 제외 */
    /* → HIRE 월차는 AnnualTransitionService 가 선처리(expiredDays 증가)하여 available=0, 여기서 자동 skip (중복 스캔 제거) */
    @Bean
    @StepScope
    public JpaCursorItemReader<VacationBalance> balanceExpiryReader(
            EntityManagerFactory emf,
            @Value("#{jobParameters['companyId']}") String companyIdStr,
            @Value("#{jobParameters['targetDate']}") String targetDateStr) {
        UUID companyId = UUID.fromString(companyIdStr);
        LocalDate targetDate = LocalDate.parse(targetDateStr);
        return new JpaCursorItemReaderBuilder<VacationBalance>()
                .name("balanceExpiryReader")
                .entityManagerFactory(emf)
                .queryString("""
                        SELECT b FROM VacationBalance b
                        JOIN FETCH b.employee
                        JOIN FETCH b.vacationType
                        WHERE b.companyId = :companyId
                          AND b.expiresAt IS NOT NULL
                          AND b.expiresAt <= :targetDate
                          AND (b.totalDays - b.usedDays - b.pendingDays - b.expiredDays) > 0
                        """)
                .parameterValues(Map.of(
                        "companyId", companyId,
                        "targetDate", targetDate))
                .build();
    }

    /* 청크 writer - balance 단위 expireBalance 위임. 예외 전파 → Spring Batch skip 메커니즘이 item 단위로 집계 */
    @Bean
    public ItemWriter<VacationBalance> balanceExpiryWriter(BalanceExpiryService balanceExpiryService) {
        // 예외를 먹지 않고 그대로 throw → .skip(Exception.class) + skipLimit 으로 위임
        return chunk -> chunk.getItems().forEach(balanceExpiryService::expireBalance);
    }
}

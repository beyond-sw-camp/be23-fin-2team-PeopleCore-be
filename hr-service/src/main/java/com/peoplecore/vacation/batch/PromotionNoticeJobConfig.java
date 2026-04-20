package com.peoplecore.vacation.batch;

import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.entity.VacationBalance;
import com.peoplecore.vacation.entity.VacationPromotionNotice;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationTypeRepository;
import com.peoplecore.vacation.service.PromotionNoticeService;
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
import java.util.UUID;

/* 연차 촉진 통지 Batch Job - 단일 Job 에 stage(FIRST/SECOND) 파라미터로 분기 */
/* JobInstance 식별자: (companyId, targetDate, stage) - 같은 조합 재실행 시 자동 dedup */
/* catch-up: expires_at BETWEEN [target - 7일, target] + UNIQUE 제약으로 중복 발송 차단 */
@Configuration
@Slf4j
public class PromotionNoticeJobConfig {

    private static final int CHUNK_SIZE = 100;

    /* 잡 실패 catch-up 일수 - 최근 N일 미통지분 함께 처리 */
    private static final int CATCH_UP_DAYS = 7;

    public static final String JOB_NAME = "promotionNoticeJob";
    public static final String STAGE_FIRST = VacationPromotionNotice.STAGE_FIRST;
    public static final String STAGE_SECOND = VacationPromotionNotice.STAGE_SECOND;

    @Bean(JOB_NAME)
    public Job promotionNoticeJob(JobRepository jobRepository, Step promotionNoticeStep,
                                  BatchFailureListener batchFailureListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(batchFailureListener)
                .start(promotionNoticeStep)
                .build();
    }

    @Bean
    public Step promotionNoticeStep(JobRepository jobRepository,
                                    PlatformTransactionManager transactionManager,
                                    JpaCursorItemReader<VacationBalance> promotionNoticeReader,
                                    ItemWriter<VacationBalance> promotionNoticeWriter) {
        return new StepBuilder("promotionNoticeStep", jobRepository)
                .<VacationBalance, VacationBalance>chunk(CHUNK_SIZE, transactionManager)
                .reader(promotionNoticeReader)
                .writer(promotionNoticeWriter)
                .build();
    }

    /* stage 별 JPQL 분기 - FIRST: 잔여 무관 / SECOND: 잔여 > 0 */
    /* expires_at BETWEEN [today + N월 - 7일, today + N월] 로 catch-up */
    @Bean
    @StepScope
    public JpaCursorItemReader<VacationBalance> promotionNoticeReader(
            EntityManagerFactory emf,
            VacationTypeRepository vacationTypeRepository,
            @Value("#{jobParameters['companyId']}") String companyIdStr,
            @Value("#{jobParameters['targetDate']}") String targetDateStr,
            @Value("#{jobParameters['monthsBefore']}") Long monthsBefore,
            @Value("#{jobParameters['stage']}") String stage) {

        UUID companyId = UUID.fromString(companyIdStr);
        LocalDate today = LocalDate.parse(targetDateStr);
        LocalDate targetTo = today.plusMonths(monthsBefore);
        LocalDate targetFrom = targetTo.minusDays(CATCH_UP_DAYS);

        VacationType annualType = vacationTypeRepository
                .findByCompanyIdAndTypeCode(companyId, VacationType.CODE_ANNUAL)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_TYPE_NOT_FOUND));

        boolean second = STAGE_SECOND.equals(stage);
        // 2차 통지는 잔여 > 0 사원만. total-used-pending-expired > 0
        String remainingFilter = second
                ? "AND (b.totalDays - b.usedDays - b.pendingDays - b.expiredDays) > 0"
                : "";

        String jpql = """
                SELECT b FROM VacationBalance b
                JOIN FETCH b.employee
                JOIN FETCH b.vacationType
                WHERE b.companyId = :companyId
                  AND b.vacationType.typeId = :typeId
                  AND b.expiresAt BETWEEN :fromDate AND :toDate
                """ + remainingFilter;

        return new JpaCursorItemReaderBuilder<VacationBalance>()
                .name("promotionNoticeReader-" + stage)
                .entityManagerFactory(emf)
                .queryString(jpql)
                .parameterValues(Map.of(
                        "companyId", companyId,
                        "typeId", annualType.getTypeId(),
                        "fromDate", targetFrom,
                        "toDate", targetTo
                ))
                .build();
    }

    /* stage 에 따라 sendFirstNotice / sendSecondNotice 호출 분기 */
    /* UNIQUE 제약으로 중복 발송은 서비스 내부에서 existsByCompany... 체크로 차단됨 */
    @Bean
    @StepScope
    public ItemWriter<VacationBalance> promotionNoticeWriter(
            PromotionNoticeService promotionNoticeService,
            @Value("#{jobParameters['stage']}") String stage) {

        boolean second = STAGE_SECOND.equals(stage);
        return chunk -> {
            for (VacationBalance balance : chunk.getItems()) {
                try {
                    if (second) {
                        promotionNoticeService.sendSecondNotice(balance);
                    } else {
                        promotionNoticeService.sendFirstNotice(balance);
                    }
                } catch (Exception e) {
                    log.error("[PromotionNoticeBatch-{}] 발송 실패 - balanceId={}, err={}",
                            stage, balance.getBalanceId(), e.getMessage(), e);
                }
            }
        };
    }
}

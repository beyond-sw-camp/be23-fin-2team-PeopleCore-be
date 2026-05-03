package com.peoplecore.vacation.batch;

import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.entity.VacationPolicy;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationPolicyRepository;
import com.peoplecore.vacation.repository.VacationTypeRepository;
import com.peoplecore.vacation.service.AnnualTransitionService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/* 1주년 월차→연차 전환 Batch Job - 회사 단위 1주년 도달 사원 일괄 처리 */
/* JobInstance 식별자: (companyId, targetDate) - 같은 회사/같은 날 재실행 자동 차단 */
/* HIRE: 월차 소멸 + 1년차 연차 발생 / FISCAL: 월차 소멸만 (Service 내부 분기) */
@Configuration
@Slf4j
public class AnnualTransitionJobConfig {

    /* 청크 단위 - transition 내부 REQUIRES_NEW. 대상자 적어 청크 의미는 약함 */
    private static final int CHUNK_SIZE = 50;

    /* 허용 skip 상한 - 1주년 사원만이라 실패 허용 폭 좁게 */
    private static final int SKIP_LIMIT = 10;

    public static final String JOB_NAME = "annualTransitionJob";

    @Bean(JOB_NAME)
    public Job annualTransitionJob(JobRepository jobRepository, Step annualTransitionStep,
                                   BatchFailureListener batchFailureListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(batchFailureListener)
                .start(annualTransitionStep)
                .build();
    }

    @Bean
    public Step annualTransitionStep(JobRepository jobRepository,
                                     PlatformTransactionManager transactionManager,
                                     JpaCursorItemReader<Employee> annualTransitionReader,
                                     ItemWriter<Employee> annualTransitionWriter) {
        return new StepBuilder("annualTransitionStep", jobRepository)
                .<Employee, Employee>chunk(CHUNK_SIZE, transactionManager)
                .reader(annualTransitionReader)
                .writer(annualTransitionWriter)
                .faultTolerant()
                .skip(Exception.class)
                .skipLimit(SKIP_LIMIT)
                .build();
    }

    /* 회사 + hireDate == today.minusYears(1) + ACTIVE/ON_LEAVE + 삭제 제외 */
    @Bean
    @StepScope
    public JpaCursorItemReader<Employee> annualTransitionReader(
            EntityManagerFactory emf,
            @Value("#{jobParameters['companyId']}") String companyIdStr,
            @Value("#{jobParameters['targetDate']}") String targetDateStr) {
        UUID companyId = UUID.fromString(companyIdStr);
        LocalDate today = LocalDate.parse(targetDateStr);
        LocalDate oneYearAgo = today.minusYears(1);

        return new JpaCursorItemReaderBuilder<Employee>()
                .name("annualTransitionReader")
                .entityManagerFactory(emf)
                .queryString("""
                        SELECT e FROM Employee e
                        WHERE e.company.companyId = :companyId
                          AND e.empHireDate = :hireDate
                          AND e.empStatus IN :statuses
                          AND e.deleteAt IS NULL
                        """)
                .parameterValues(Map.of(
                        "companyId", companyId,
                        "hireDate", oneYearAgo,
                        "statuses", List.of(EmpStatus.ACTIVE, EmpStatus.ON_LEAVE)
                ))
                .build();
    }

    /* 청크 writer - 정책/유형 StepScope 1회 로드 후 재사용. transition 위임 */
    /* 정책/유형 누락은 Scheduler 선검증으로 사실상 도달 X. 잔여 안전망 */
    @Bean
    @StepScope
    public ItemWriter<Employee> annualTransitionWriter(
            AnnualTransitionService annualTransitionService,
            VacationPolicyRepository vacationPolicyRepository,
            VacationTypeRepository vacationTypeRepository,
            @Value("#{jobParameters['companyId']}") String companyIdStr,
            @Value("#{jobParameters['targetDate']}") String targetDateStr) {

        UUID companyId = UUID.fromString(companyIdStr);
        LocalDate today = LocalDate.parse(targetDateStr);

        VacationPolicy policy = vacationPolicyRepository.findByCompanyIdFetchRules(companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_POLICY_NOT_FOUND));
        VacationType monthlyType = vacationTypeRepository
                .findByCompanyIdAndTypeCode(companyId, VacationType.CODE_MONTHLY)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_TYPE_NOT_FOUND));
        VacationType annualType = vacationTypeRepository
                .findByCompanyIdAndTypeCode(companyId, VacationType.CODE_ANNUAL)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_TYPE_NOT_FOUND));

        return chunk -> {
            for (Employee emp : chunk.getItems()) {
                annualTransitionService.transition(companyId, emp, policy, monthlyType, annualType, today);
            }
        };
    }
}

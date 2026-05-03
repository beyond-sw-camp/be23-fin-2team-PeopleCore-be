package com.peoplecore.vacation.batch;

import com.peoplecore.employee.domain.EmpGender;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationTypeRepository;
import com.peoplecore.vacation.service.MenstrualMonthlyGrantService;
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

/* 생리휴가 월별 부여 Batch Job - 회사 단위 여성 ACTIVE 사원 일괄 적립 + 전월 만료 */
/* JobInstance 식별자: (companyId, targetDate) - 같은 회사/같은 날 재실행 자동 차단 */
/* MENSTRUAL 유형 활성 + Female + ACTIVE + 삭제 제외 사원만 처리 */
@Configuration
@Slf4j
public class MenstrualMonthlyGrantJobConfig {

    /* 청크 단위 - grantForEmployee 내부 REQUIRES_NEW. 외부 tx 비어있음 */
    private static final int CHUNK_SIZE = 100;

    /* 허용 skip 상한 - 초과 시 Step FAILED → Discord 빨간색 알림 */
    private static final int SKIP_LIMIT = 30;

    public static final String JOB_NAME = "menstrualMonthlyGrantJob";

    @Bean(JOB_NAME)
    public Job menstrualMonthlyGrantJob(JobRepository jobRepository, Step menstrualMonthlyGrantStep,
                                        BatchFailureListener batchFailureListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(batchFailureListener)
                .start(menstrualMonthlyGrantStep)
                .build();
    }

    @Bean
    public Step menstrualMonthlyGrantStep(JobRepository jobRepository,
                                          PlatformTransactionManager transactionManager,
                                          JpaCursorItemReader<Employee> menstrualMonthlyGrantReader,
                                          ItemWriter<Employee> menstrualMonthlyGrantWriter) {
        return new StepBuilder("menstrualMonthlyGrantStep", jobRepository)
                .<Employee, Employee>chunk(CHUNK_SIZE, transactionManager)
                .reader(menstrualMonthlyGrantReader)
                .writer(menstrualMonthlyGrantWriter)
                .faultTolerant()
                .skip(Exception.class)
                .skipLimit(SKIP_LIMIT)
                .build();
    }

    /* 회사 + Female + ACTIVE + 삭제 제외 사원 커서 조회 */
    @Bean
    @StepScope
    public JpaCursorItemReader<Employee> menstrualMonthlyGrantReader(
            EntityManagerFactory emf,
            @Value("#{jobParameters['companyId']}") String companyIdStr) {
        UUID companyId = UUID.fromString(companyIdStr);
        return new JpaCursorItemReaderBuilder<Employee>()
                .name("menstrualMonthlyGrantReader")
                .entityManagerFactory(emf)
                .queryString("""
                        SELECT e FROM Employee e
                        WHERE e.company.companyId = :companyId
                          AND e.empGender = :gender
                          AND e.empStatus = :status
                          AND e.deleteAt IS NULL
                        """)
                .parameterValues(Map.of(
                        "companyId", companyId,
                        "gender", EmpGender.FEMALE,
                        "status", EmpStatus.ACTIVE
                ))
                .build();
    }

    /* 청크 writer - menstrualType StepScope 1회 로드 후 사원당 grantForEmployee 위임 */
    /* MENSTRUAL 유형 누락/비활성은 Scheduler 선검증으로 사실상 도달 X. 잔여 안전망 */
    @Bean
    @StepScope
    public ItemWriter<Employee> menstrualMonthlyGrantWriter(
            MenstrualMonthlyGrantService menstrualMonthlyGrantService,
            VacationTypeRepository vacationTypeRepository,
            @Value("#{jobParameters['companyId']}") String companyIdStr,
            @Value("#{jobParameters['targetDate']}") String targetDateStr) {

        UUID companyId = UUID.fromString(companyIdStr);
        LocalDate today = LocalDate.parse(targetDateStr);

        VacationType menstrualType = vacationTypeRepository
                .findByCompanyIdAndTypeCode(companyId, "MENSTRUAL")
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_TYPE_NOT_FOUND));

        return chunk -> {
            for (Employee emp : chunk.getItems()) {
                menstrualMonthlyGrantService.grantForEmployee(companyId, emp, menstrualType, today);
            }
        };
    }
}

package com.peoplecore.vacation.batch;

import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.entity.VacationPolicy;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationPolicyRepository;
import com.peoplecore.vacation.repository.VacationTypeRepository;
import com.peoplecore.vacation.service.AnnualGrantService;
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

/* 연차 발생(FISCAL) Batch Job - 회계연도 시작일에 회사별 전사원 일괄 연차 발생 */
/* JobInstance 식별자: (companyId, targetDate) - 같은 회사/같은 날 재실행 자동 차단 */
@Configuration
@Slf4j
public class AnnualGrantFiscalJobConfig {

    /* 청크 단위 - grantForFiscal 내부 REQUIRES_NEW 라 외부 tx 는 비어있음 */
    private static final int CHUNK_SIZE = 200;

    public static final String JOB_NAME = "annualGrantFiscalJob";

    @Bean(JOB_NAME)
    public Job annualGrantFiscalJob(JobRepository jobRepository, Step annualGrantFiscalStep,
                                    BatchFailureListener batchFailureListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(batchFailureListener)
                .start(annualGrantFiscalStep)
                .build();
    }

    @Bean
    public Step annualGrantFiscalStep(JobRepository jobRepository,
                                      PlatformTransactionManager transactionManager,
                                      JpaCursorItemReader<Employee> annualGrantFiscalReader,
                                      ItemWriter<Employee> annualGrantFiscalWriter) {
        return new StepBuilder("annualGrantFiscalStep", jobRepository)
                .<Employee, Employee>chunk(CHUNK_SIZE, transactionManager)
                .reader(annualGrantFiscalReader)
                .writer(annualGrantFiscalWriter)
                .build();
    }

    /* 회사 + 상태 조건 사원 커서 조회 - 삭제 제외 */
    @Bean
    @StepScope
    public JpaCursorItemReader<Employee> annualGrantFiscalReader(
            EntityManagerFactory emf,
            @Value("#{jobParameters['companyId']}") String companyIdStr) {
        UUID companyId = UUID.fromString(companyIdStr);
        return new JpaCursorItemReaderBuilder<Employee>()
                .name("annualGrantFiscalReader")
                .entityManagerFactory(emf)
                .queryString("""
                        SELECT e FROM Employee e
                        WHERE e.company.companyId = :companyId
                          AND e.empStatus IN :statuses
                          AND e.deleteAt IS NULL
                        """)
                .parameterValues(Map.of(
                        "companyId", companyId,
                        "statuses", List.of(EmpStatus.ACTIVE, EmpStatus.ON_LEAVE)
                ))
                .build();
    }

    /* 청크 writer - policy/annualType 은 StepScope 생성 시 1회 로드 후 재사용 */
    @Bean
    @StepScope
    public ItemWriter<Employee> annualGrantFiscalWriter(
            AnnualGrantService annualGrantService,
            VacationPolicyRepository vacationPolicyRepository,
            VacationTypeRepository vacationTypeRepository,
            @Value("#{jobParameters['companyId']}") String companyIdStr,
            @Value("#{jobParameters['targetDate']}") String targetDateStr) {

        UUID companyId = UUID.fromString(companyIdStr);
        LocalDate targetDate = LocalDate.parse(targetDateStr);

        // step 시작 시점 1회 로드 - 만약 없으면 정책/유형 누락으로 VACATION_POLICY_NOT_FOUND 발생
        VacationPolicy policy = vacationPolicyRepository.findByCompanyIdFetchRules(companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_POLICY_NOT_FOUND));
        VacationType annualType = vacationTypeRepository
                .findByCompanyIdAndTypeCode(companyId, VacationType.CODE_ANNUAL)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_TYPE_NOT_FOUND));

        return chunk -> {
            for (Employee emp : chunk.getItems()) {
                try {
                    annualGrantService.grantForFiscal(companyId, emp, annualType, policy, targetDate);
                } catch (Exception e) {
                    log.error("[AnnualGrantFiscalBatch] 사원 처리 실패 - companyId={}, empId={}, err={}",
                            companyId, emp.getEmpId(), e.getMessage(), e);
                }
            }
        };
    }
}

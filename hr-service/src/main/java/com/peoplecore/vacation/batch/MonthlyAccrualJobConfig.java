package com.peoplecore.vacation.batch;

import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationTypeRepository;
import com.peoplecore.vacation.service.MonthlyAccrualService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/* 월차 적립 Batch Job - 회사 단위 1~11개월차 사원 일괄 적립 */
/* JobInstance 식별자: (companyId, targetDate) - 같은 회사/같은 날 재실행 자동 차단 */
@Configuration
@Slf4j
public class MonthlyAccrualJobConfig {

    /* 청크 단위 - accrueIfEligible 내부 REQUIRES_NEW 라 외부 tx 는 비어있음 */
    private static final int CHUNK_SIZE = 100;

    /* 허용 skip 상한 - 초과 시 Step FAILED → Discord 빨간색 알림 */
    private static final int SKIP_LIMIT = 30;

    /* 월차 적립 대상 개월차 - 1~11개월차. 12개월(1년) 은 AnnualTransition 담당 */
    private static final int MAX_ACCRUAL_MONTH = 11;

    public static final String JOB_NAME = "monthlyAccrualJob";

    @Bean(JOB_NAME)
    public Job monthlyAccrualJob(JobRepository jobRepository, Step monthlyAccrualStep,
                                 BatchFailureListener batchFailureListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(batchFailureListener)
                .start(monthlyAccrualStep)
                .build();
    }

    @Bean
    public Step monthlyAccrualStep(JobRepository jobRepository,
                                   PlatformTransactionManager transactionManager,
                                   JpaCursorItemReader<Employee> monthlyAccrualReader,
                                   ItemWriter<Employee> monthlyAccrualWriter) {
        return new StepBuilder("monthlyAccrualStep", jobRepository)
                .<Employee, Employee>chunk(CHUNK_SIZE, transactionManager)
                .reader(monthlyAccrualReader)
                .writer(monthlyAccrualWriter)
                .faultTolerant()
                .skip(Exception.class)
                .skipLimit(SKIP_LIMIT)
                .build();
    }

    /* 회사 + 1~11개월차 입사일 IN + ACTIVE/ON_LEAVE + 삭제 제외 사원 커서 조회 */
    /* 입사일 11개 사전 계산 → IN 쿼리 1회로 벌크. 기존 Scheduler 의 IN 쿼리 패턴 보존 */
    @Bean
    @StepScope
    public JpaCursorItemReader<Employee> monthlyAccrualReader(
            EntityManagerFactory emf,
            @Value("#{jobParameters['companyId']}") String companyIdStr,
            @Value("#{jobParameters['targetDate']}") String targetDateStr) {
        UUID companyId = UUID.fromString(companyIdStr);
        LocalDate today = LocalDate.parse(targetDateStr);

        List<LocalDate> targetHireDates = new ArrayList<>(MAX_ACCRUAL_MONTH);
        for (int n = 1; n <= MAX_ACCRUAL_MONTH; n++) {
            targetHireDates.add(today.minusMonths(n));
        }

        return new JpaCursorItemReaderBuilder<Employee>()
                .name("monthlyAccrualReader")
                .entityManagerFactory(emf)
                .queryString("""
                        SELECT e FROM Employee e
                        WHERE e.company.companyId = :companyId
                          AND e.empHireDate IN :hireDates
                          AND e.empStatus IN :statuses
                          AND e.deleteAt IS NULL
                        """)
                .parameterValues(Map.of(
                        "companyId", companyId,
                        "hireDates", targetHireDates,
                        "statuses", List.of(EmpStatus.ACTIVE, EmpStatus.ON_LEAVE)
                ))
                .build();
    }

    /* 청크 writer - 사원당 hireDate → monthNth 역산 후 accrueIfEligible 위임 */
    /* monthlyType 은 StepScope 1회 로드 후 재사용 */
    /* 예외 전파 → .skip(Exception.class) 로 item 단위 집계 */
    @Bean
    @StepScope
    public ItemWriter<Employee> monthlyAccrualWriter(
            MonthlyAccrualService monthlyAccrualService,
            VacationTypeRepository vacationTypeRepository,
            @Value("#{jobParameters['companyId']}") String companyIdStr,
            @Value("#{jobParameters['targetDate']}") String targetDateStr) {

        UUID companyId = UUID.fromString(companyIdStr);
        LocalDate today = LocalDate.parse(targetDateStr);

        // step 시작 1회 로드 - Scheduler 선검증으로 사실상 도달 X. 만에 하나 누락 시 VACATION_TYPE_NOT_FOUND
        VacationType monthlyType = vacationTypeRepository
                .findByCompanyIdAndTypeCode(companyId, VacationType.CODE_MONTHLY)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_TYPE_NOT_FOUND));

        return chunk -> {
            for (Employee emp : chunk.getItems()) {
                int monthNth = resolveMonthNth(emp.getEmpHireDate(), today);
                if (monthNth < 1) {
                    // reader IN 필터로 사실상 도달 X. 비정상 케이스 방어
                    log.warn("[MonthlyAccrual] monthNth 매핑 실패 - empId={}, hireDate={}, today={}",
                            emp.getEmpId(), emp.getEmpHireDate(), today);
                    continue;
                }
                monthlyAccrualService.accrueIfEligible(companyId, emp, monthlyType, monthNth, today);
            }
        };
    }

    /* hireDate 가 today.minusMonths(n) 와 정확히 일치하는 n 반환 (1..11). 미일치 시 -1 */
    private static int resolveMonthNth(LocalDate hireDate, LocalDate today) {
        for (int n = 1; n <= MAX_ACCRUAL_MONTH; n++) {
            if (today.minusMonths(n).equals(hireDate)) return n;
        }
        return -1;
    }
}

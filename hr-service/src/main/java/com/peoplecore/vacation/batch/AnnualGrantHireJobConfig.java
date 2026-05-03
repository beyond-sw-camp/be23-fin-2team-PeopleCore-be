package com.peoplecore.vacation.batch;

import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.entity.VacationPolicy;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationPolicyRepository;
import com.peoplecore.vacation.repository.VacationTypeRepository;
import com.peoplecore.vacation.service.AnnualGrantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/* HIRE 정책 연차 발생 Batch Job - 입사기념일 도래 사원 일괄 처리 */
/* JobInstance 식별자: (companyId, targetDate) - 같은 회사/같은 날 재실행 자동 차단 */
/* 2/29 보정 - 비윤년 3/1 fire 시 2/29 입사자도 기념일 도래로 포함 */
@Configuration
@Slf4j
public class AnnualGrantHireJobConfig {

    /* 청크 단위 - grantForHire 내부 REQUIRES_NEW. 입사기념일 매치자만이라 회사당 보통 0~수십 명 */
    private static final int CHUNK_SIZE = 50;

    /* 허용 skip 상한 */
    private static final int SKIP_LIMIT = 20;

    /* 2주년부터 발생 (1주년은 AnnualTransition 담당) */
    private static final int MIN_HIRE_YEARS_OF_SERVICE = 2;

    public static final String JOB_NAME = "annualGrantHireJob";

    @Bean(JOB_NAME)
    public Job annualGrantHireJob(JobRepository jobRepository, Step annualGrantHireStep,
                                  BatchFailureListener batchFailureListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(batchFailureListener)
                .start(annualGrantHireStep)
                .build();
    }

    @Bean
    public Step annualGrantHireStep(JobRepository jobRepository,
                                    PlatformTransactionManager transactionManager,
                                    ListItemReader<Employee> annualGrantHireReader,
                                    ItemWriter<Employee> annualGrantHireWriter) {
        return new StepBuilder("annualGrantHireStep", jobRepository)
                .<Employee, Employee>chunk(CHUNK_SIZE, transactionManager)
                .reader(annualGrantHireReader)
                .writer(annualGrantHireWriter)
                .faultTolerant()
                .skip(Exception.class)
                .skipLimit(SKIP_LIMIT)
                .build();
    }

    /* 회사 + 오늘 입사기념일 매치 사원 조회 (+ 비윤년 3/1 → 2/29 보정 병합) */
    /* JpaCursorItemReader 가 아닌 ListItemReader - hireMonth/Day 쿼리는 native, 매치자 적어 인메모리 안전 */
    @Bean
    @StepScope
    public ListItemReader<Employee> annualGrantHireReader(
            EmployeeRepository employeeRepository,
            @Value("#{jobParameters['companyId']}") String companyIdStr,
            @Value("#{jobParameters['targetDate']}") String targetDateStr) {

        UUID companyId = UUID.fromString(companyIdStr);
        LocalDate today = LocalDate.parse(targetDateStr);
        List<EmpStatus> statuses = List.of(EmpStatus.ACTIVE, EmpStatus.ON_LEAVE);

        List<Employee> emps = new ArrayList<>(employeeRepository.findByCompanyIdAndHireMonthDayAndEmpStatusIn(
                companyId, today.getMonthValue(), today.getDayOfMonth(), statuses));

        // 2/29 보정 - 비윤년 3/1 fire 시 2/29 입사자도 도래로 처리
        boolean includeFeb29 = today.getMonthValue() == 3 && today.getDayOfMonth() == 1 && !today.isLeapYear();
        if (includeFeb29) {
            List<Employee> feb29Emps = employeeRepository
                    .findByCompanyIdAndHireMonthDayAndEmpStatusIn(companyId, 2, 29, statuses);
            Set<Long> existing = new HashSet<>(emps.size());
            for (Employee e : emps) existing.add(e.getEmpId());
            for (Employee e : feb29Emps) {
                if (existing.add(e.getEmpId())) emps.add(e);
            }
        }
        return new ListItemReader<>(emps);
    }

    /* 청크 writer - 정책/유형 StepScope 1회 로드. yearsOfService 계산 후 2년 미만 skip */
    /* 1년차는 AnnualTransition 담당이라 reader 단계에서 거를 수 없음 (입사기념일 == today 만 필터) → writer 에서 가드 */
    @Bean
    @StepScope
    public ItemWriter<Employee> annualGrantHireWriter(
            AnnualGrantService annualGrantService,
            VacationPolicyRepository vacationPolicyRepository,
            VacationTypeRepository vacationTypeRepository,
            @Value("#{jobParameters['companyId']}") String companyIdStr,
            @Value("#{jobParameters['targetDate']}") String targetDateStr) {

        UUID companyId = UUID.fromString(companyIdStr);
        LocalDate today = LocalDate.parse(targetDateStr);

        VacationPolicy policy = vacationPolicyRepository.findByCompanyIdFetchRules(companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_POLICY_NOT_FOUND));
        VacationType annualType = vacationTypeRepository
                .findByCompanyIdAndTypeCode(companyId, VacationType.CODE_ANNUAL)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_TYPE_NOT_FOUND));

        return chunk -> {
            for (Employee emp : chunk.getItems()) {
                int yearsOfService = (int) ChronoUnit.YEARS.between(emp.getEmpHireDate(), today);
                if (yearsOfService < MIN_HIRE_YEARS_OF_SERVICE) {
                    log.debug("[AnnualGrant-HIRE] 1년차 skip - empId={}", emp.getEmpId());
                    continue;
                }
                annualGrantService.grantForHire(companyId, emp, annualType, policy, yearsOfService, today);
            }
        };
    }
}

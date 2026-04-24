package com.peoplecore.vacation.scheduler;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.domain.CompanyStatus;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.vacation.batch.AnnualGrantFiscalJobConfig;
import com.peoplecore.vacation.entity.VacationPolicy;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationPolicyRepository;
import com.peoplecore.vacation.repository.VacationTypeRepository;
import com.peoplecore.vacation.service.AnnualGrantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/* 연차 발생 스케줄러 - 매일 자정 실행. 회사별 정책(HIRE/FISCAL) 따라 분기 */
/* HIRE: 입사기념일(MONTH+DAY) 매치 사원 처리 */
/* FISCAL: 오늘 = 회계연도 시작일 일 때만 전 사원 처리 */
/* 분산 락 1회 획득 후 회사 순회. 사원 단위 실패는 AnnualGrantService 가 REQUIRES_NEW 로 격리 */
@Component
@Slf4j
public class AnnualGrantScheduler {

    /* 2주년부터 연차 발생 (1주년은 AnnualTransitionScheduler 담당) */
    private static final int MIN_HIRE_YEARS_OF_SERVICE = 2;

    /* 분산 락 TTL */
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    /* 락 키 prefix - 날짜 단위 멱등 */
    private static final String LOCK_KEY_PREFIX = "annual-grant";

    /* 타임존 고정 - Asia/Seoul */
    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final StringRedisTemplate redisTemplate;
    private final CompanyRepository companyRepository;
    private final VacationPolicyRepository vacationPolicyRepository;
    private final VacationTypeRepository vacationTypeRepository;
    private final EmployeeRepository employeeRepository;
    private final AnnualGrantService annualGrantService;
    private final JobLauncher jobLauncher;
    private final Job annualGrantFiscalJob;

    @Autowired
    public AnnualGrantScheduler(StringRedisTemplate redisTemplate,
                                CompanyRepository companyRepository,
                                VacationPolicyRepository vacationPolicyRepository,
                                VacationTypeRepository vacationTypeRepository,
                                EmployeeRepository employeeRepository,
                                AnnualGrantService annualGrantService,
                                JobLauncher jobLauncher,
                                @Qualifier(AnnualGrantFiscalJobConfig.JOB_NAME) Job annualGrantFiscalJob) {
        this.redisTemplate = redisTemplate;
        this.companyRepository = companyRepository;
        this.vacationPolicyRepository = vacationPolicyRepository;
        this.vacationTypeRepository = vacationTypeRepository;
        this.employeeRepository = employeeRepository;
        this.annualGrantService = annualGrantService;
        this.jobLauncher = jobLauncher;
        this.annualGrantFiscalJob = annualGrantFiscalJob;
    }

    /* 매일 00:10 KST - MonthlyAccrual(00:00), AnnualTransition(00:05) 후 실행 */
    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    public void run() {
        LocalDate today = LocalDate.now(ZONE_SEOUL);
        String lockKey = LOCK_KEY_PREFIX + ":" + today;

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("[AnnualGrant] 다른 인스턴스 진행 중 - skip. date={}", today);
            return;
        }
        log.info("[AnnualGrant] 시작 - date={}", today);
        try {


            List<Company> activeCompanies = companyRepository.findByCompanyStatus(CompanyStatus.ACTIVE);
            int processed = 0;
            for (Company company : activeCompanies) {
                try {
                    processCompany(company, today);
                    processed++;
                } catch (Exception e) {
                    log.error("[AnnualGrant] 회사 처리 실패 - companyId={}, err={}",
                            company.getCompanyId(), e.getMessage(), e);
                }
            }
            log.info("[AnnualGrant] 완료 - date={}, companies={}/{}", today, processed, activeCompanies.size());
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    /* 회사 단위 - 정책 조회 후 HIRE/FISCAL 분기 */
    private void processCompany(Company company, LocalDate today) {
        UUID companyId = company.getCompanyId();

        VacationPolicy policy = vacationPolicyRepository.findByCompanyIdFetchRules(companyId).orElse(null);
        if (policy == null) {
            log.warn("[AnnualGrant] 정책 없음 - companyId={} (initDefault 누락?)", companyId);
            return;
        }

        VacationType annualType = vacationTypeRepository
                .findByCompanyIdAndTypeCode(companyId, VacationType.CODE_ANNUAL)
                .orElse(null);
        if (annualType == null) {
            log.warn("[AnnualGrant] ANNUAL 유형 없음 - companyId={} (initDefault 누락?)", companyId);
            return;
        }

        switch (policy.getPolicyBaseType()) {
            case HIRE -> processHire(company, policy, annualType, today);
            case FISCAL -> processFiscal(company, policy, annualType, today);
        }
    }

    /* HIRE - 오늘의 MONTH+DAY 로 입사기념일 매치 사원 조회 */
    /* 근속 2년 이상만 처리 (1년차는 AnnualTransition 담당) */
    private void processHire(Company company, VacationPolicy policy, VacationType annualType, LocalDate today) {
        UUID companyId = company.getCompanyId();

        /*2/29일 입사자 보정 - 비윤년 3/1에 2/29입사자도 기념일 도래로 판정 오늘이 3/1일이면서 윤년이 아니어야 함  */
        boolean includeFeb29 = today.getMonthValue() == 3 && today.getDayOfMonth() == 1 && !today.isLeapYear();

        List<Employee> emps = new ArrayList<>(employeeRepository.findByCompanyIdAndHireMonthDayAndEmpStatusIn(
                companyId,
                today.getMonthValue(),
                today.getDayOfMonth(),
                List.of(EmpStatus.ACTIVE, EmpStatus.ON_LEAVE)));

        if (includeFeb29) {
            List<Employee> feb29Emps = employeeRepository.findByCompanyIdAndHireMonthDayAndEmpStatusIn(companyId, 2, 29, List.of(EmpStatus.ACTIVE, EmpStatus.ON_LEAVE));
            /*중복 방지 후 병합 */
            Set<Long> existing = emps.stream().map(Employee::getEmpId).collect(Collectors.toSet());
            for (Employee e : feb29Emps) {
                if (!existing.contains(e.getEmpId())) emps.add(e);
            }
        }

        if (emps.isEmpty()) return;

        log.info("[AnnualGrant-HIRE] companyId={}, 대상={}명", companyId, emps.size());

        for (Employee emp : emps) {
            /*chronoUnit으로 만 연수 계산 - 월/일까지 고려 입사일 기준으로 정확히 1년이상이어야 함  today>= 기념일  */
            int yearsOfService = (int) ChronoUnit.YEARS.between(emp.getEmpHireDate(), today);
            if (yearsOfService < MIN_HIRE_YEARS_OF_SERVICE) {
                log.debug("[AnnualGrant-HIRE] 1년차 - AnnualTransition 담당. empId={}", emp.getEmpId());
                continue;
            }
            try {
                annualGrantService.grantForHire(companyId, emp, annualType, policy, yearsOfService, today);
            } catch (Exception e) {
                log.error("[AnnualGrant-HIRE] 사원 처리 실패 - empId={}, err={}",
                        emp.getEmpId(), e.getMessage(), e);
            }
        }
    }

    /* FISCAL - 오늘이 회계연도 시작일일 때만 Batch Job 런칭 */
    /* 회계연도 시작일은 01-01 로 고정 (회사별 커스텀 폐지) */
    /* 회사별 전사원 대상 → Spring Batch 로 위임 (청크 tx / 재시작 / dedup) */
    private void processFiscal(Company company, VacationPolicy policy, VacationType annualType, LocalDate today) {
        UUID companyId = company.getCompanyId();
        String fiscalStart = policy.getPolicyFiscalYearStart();
        if (fiscalStart == null || fiscalStart.isBlank()) {
            log.warn("[AnnualGrant-FISCAL] fiscal_year_start null - companyId={}", companyId);
            return;
        }

        String todayMmDd = String.format("%02d-%02d", today.getMonthValue(), today.getDayOfMonth());
        if (!fiscalStart.equals(todayMmDd)) return;

        log.info("[AnnualGrant-FISCAL] Batch 런칭 - companyId={}, fiscalStart={}, today={}",
                companyId, fiscalStart, todayMmDd);
        try {
            // (companyId, targetDate) 식별 파라미터 → 같은 회사 같은 날 중복 자동 차단
            JobParameters params = new JobParametersBuilder()
                    .addString("companyId", companyId.toString())
                    .addString("targetDate", today.toString())
                    .toJobParameters();
            jobLauncher.run(annualGrantFiscalJob, params);
        } catch (JobInstanceAlreadyCompleteException e) {
            log.info("[AnnualGrant-FISCAL] 이미 완료 - companyId={}, date={}", companyId, today);
        } catch (Exception e) {
            log.error("[AnnualGrant-FISCAL] Batch 실행 실패 - companyId={}, err={}",
                    companyId, e.getMessage(), e);
        }
    }
}
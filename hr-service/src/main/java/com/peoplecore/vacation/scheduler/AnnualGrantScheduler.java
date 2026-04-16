package com.peoplecore.vacation.scheduler;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.domain.CompanyStatus;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.vacation.entity.VacationPolicy;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationPolicyRepository;
import com.peoplecore.vacation.repository.VacationTypeRepository;
import com.peoplecore.vacation.service.AnnualGrantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

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

    @Autowired
    public AnnualGrantScheduler(StringRedisTemplate redisTemplate,
                                CompanyRepository companyRepository,
                                VacationPolicyRepository vacationPolicyRepository,
                                VacationTypeRepository vacationTypeRepository,
                                EmployeeRepository employeeRepository,
                                AnnualGrantService annualGrantService) {
        this.redisTemplate = redisTemplate;
        this.companyRepository = companyRepository;
        this.vacationPolicyRepository = vacationPolicyRepository;
        this.vacationTypeRepository = vacationTypeRepository;
        this.employeeRepository = employeeRepository;
        this.annualGrantService = annualGrantService;
    }

    /* 매일 00:00 KST - 분산 락 1회 후 활성 회사 순회 */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void run() {
        LocalDate today = LocalDate.now(ZONE_SEOUL);
        String lockKey = LOCK_KEY_PREFIX + ":" + today;

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("[AnnualGrant] 다른 인스턴스 진행 중 - skip. date={}", today);
            return;
        }
        log.info("[AnnualGrant] 시작 - date={}", today);

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
            case HIRE   -> processHire(company, policy, annualType, today);
            case FISCAL -> processFiscal(company, policy, annualType, today);
        }
    }

    /* HIRE - 오늘의 MONTH+DAY 로 입사기념일 매치 사원 조회 */
    /* 근속 2년 이상만 처리 (1년차는 AnnualTransition 담당) */
    private void processHire(Company company, VacationPolicy policy, VacationType annualType, LocalDate today) {
        UUID companyId = company.getCompanyId();
        List<Employee> emps = employeeRepository.findByCompanyIdAndHireMonthDayAndEmpStatusIn(
                companyId,
                today.getMonthValue(),
                today.getDayOfMonth(),
                List.of(EmpStatus.ACTIVE, EmpStatus.ON_LEAVE));
        if (emps.isEmpty()) return;

        log.info("[AnnualGrant-HIRE] companyId={}, 대상={}명", companyId, emps.size());

        for (Employee emp : emps) {
            int yearsOfService = today.getYear() - emp.getEmpHireDate().getYear();
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

    /* FISCAL - 오늘이 회계연도 시작일일 때만 전 사원 처리 */
    private void processFiscal(Company company, VacationPolicy policy, VacationType annualType, LocalDate today) {
        UUID companyId = company.getCompanyId();
        String fiscalStart = policy.getPolicyFiscalYearStart();
        if (fiscalStart == null || fiscalStart.isBlank()) {
            log.warn("[AnnualGrant-FISCAL] fiscal_year_start null - companyId={}", companyId);
            return;
        }

        String todayMmDd = String.format("%02d-%02d", today.getMonthValue(), today.getDayOfMonth());
        if (!fiscalStart.equals(todayMmDd)) return;

        List<Employee> emps = employeeRepository
                .findByCompany_CompanyIdAndEmpStatusInAndDeleteAtIsNull(
                        companyId, List.of(EmpStatus.ACTIVE, EmpStatus.ON_LEAVE));
        log.info("[AnnualGrant-FISCAL] companyId={}, fiscalStart={}, 대상={}명",
                companyId, fiscalStart, emps.size());

        for (Employee emp : emps) {
            try {
                annualGrantService.grantForFiscal(companyId, emp, annualType, policy, today);
            } catch (Exception e) {
                log.error("[AnnualGrant-FISCAL] 사원 처리 실패 - empId={}, err={}",
                        emp.getEmpId(), e.getMessage(), e);
            }
        }
    }
}
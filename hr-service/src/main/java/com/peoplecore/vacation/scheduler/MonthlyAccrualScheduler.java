package com.peoplecore.vacation.scheduler;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.domain.CompanyStatus;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationTypeRepository;
import com.peoplecore.vacation.service.MonthlyAccrualService;
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

/* 월차 자동 적립 스케줄러 - 매일 자정 실행 */
/* 분산 락으로 멀티 인스턴스 중복 실행 방지 (락 키: monthly-accrual:{yyyy-MM-dd}) */
/* 사원 단위 처리는 MonthlyAccrualService 로 위임 (REQUIRES_NEW 격리) */
@Component
@Slf4j
public class MonthlyAccrualScheduler {

    /* 월차 적립 대상 개월차 - 입사 1~11개월차. 12개월(1년)은 AnnualTransitionScheduler 가 담당 */
    private static final int MAX_ACCRUAL_MONTH = 11;

    /* 분산 락 TTL - 스케줄러 최대 실행 시간 여유 */
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    /* 락 키 prefix */
    private static final String LOCK_KEY_PREFIX = "monthly-accrual";

    /* KST 고정 - 서버 로컬 타임존과 무관하게 정책 시각 준수 */
    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final StringRedisTemplate redisTemplate;
    private final CompanyRepository companyRepository;
    private final VacationTypeRepository vacationTypeRepository;
    private final EmployeeRepository employeeRepository;
    private final MonthlyAccrualService monthlyAccrualService;

    @Autowired
    public MonthlyAccrualScheduler(StringRedisTemplate redisTemplate,
                                   CompanyRepository companyRepository,
                                   VacationTypeRepository vacationTypeRepository,
                                   EmployeeRepository employeeRepository,
                                   MonthlyAccrualService monthlyAccrualService) {
        this.redisTemplate = redisTemplate;
        this.companyRepository = companyRepository;
        this.vacationTypeRepository = vacationTypeRepository;
        this.employeeRepository = employeeRepository;
        this.monthlyAccrualService = monthlyAccrualService;
    }

    /* 매일 00:00 KST 실행 - 전날 만근 판정 대상 사원 월차 적립 */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void run() {
        LocalDate today = LocalDate.now(ZONE_SEOUL);
        String lockKey = LOCK_KEY_PREFIX + ":" + today;

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("[MonthlyAccrual] 다른 인스턴스 진행 중 - skip. date={}", today);
            return;
        }
        log.info("[MonthlyAccrual] 시작 - date={}", today);
        try {
            List<Company> activeCompanies = companyRepository.findByCompanyStatus(CompanyStatus.ACTIVE);
            int processedCompanies = 0;
            for (Company company : activeCompanies) {
                try {
                    processCompany(company, today);
                    processedCompanies++;
                } catch (Exception e) {
                    log.error("[MonthlyAccrual] 회사 처리 실패 - companyId={}, err={}",
                            company.getCompanyId(), e.getMessage(), e);
                }
            }
            log.info("[MonthlyAccrual] 완료 - date={}, companies={}/{}", today, processedCompanies, activeCompanies.size());
        } finally {
            /* 스케줄러 종료시 즉시 해제 -> schedular가 얼마만에 끝날지 모르기 때문에 */
            redisTemplate.delete(lockKey);
        }
    }

    /* 회사 단위 처리 - MONTHLY 유형 조회 + N=1~11 루프 */
    private void processCompany(Company company, LocalDate today) {
        UUID companyId = company.getCompanyId();
        VacationType monthlyType = vacationTypeRepository
                .findByCompanyIdAndTypeCode(companyId, VacationType.CODE_MONTHLY)
                .orElse(null);
        if (monthlyType == null) {
            log.warn("[MonthlyAccrual] MONTHLY 유형 없음 - companyId={} (initDefault 누락?)", companyId);
            return;
        }

        for (int n = 1; n <= MAX_ACCRUAL_MONTH; n++) {
            LocalDate targetHireDate = today.minusMonths(n);
            List<Employee> targets = employeeRepository
                    .findByCompany_CompanyIdAndEmpHireDateAndEmpStatusInAndDeleteAtIsNull(
                            companyId, targetHireDate,
                            List.of(EmpStatus.ACTIVE, EmpStatus.ON_LEAVE));
            for (Employee emp : targets) {
                try {
                    monthlyAccrualService.accrueIfEligible(companyId, emp, monthlyType, n, today);
                } catch (Exception e) {
                    log.error("[MonthlyAccrual] 사원 처리 실패 - companyId={}, empId={}, n={}, err={}",
                            companyId, emp.getEmpId(), n, e.getMessage(), e);
                }
            }
        }
    }
}
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

    /* 회사 단위 처리 - MONTHLY 유형 조회 + 1~11개월차 대상 IN 쿼리 1회로 벌크 조회 */
    /* 기존: n마다 개별 쿼리 11회 → 개선: IN (날짜11개) 1회 + 메모리 그룹핑 */
    private void processCompany(Company company, LocalDate today) {
        UUID companyId = company.getCompanyId();
        VacationType monthlyType = vacationTypeRepository
                .findByCompanyIdAndTypeCode(companyId, VacationType.CODE_MONTHLY)
                .orElse(null);
        if (monthlyType == null) {
            log.warn("[MonthlyAccrual] MONTHLY 유형 없음 - companyId={} (initDefault 누락?)", companyId);
            return;
        }

        /* 1~11개월차 대상 입사일 11개 사전 계산 */
        List<LocalDate> targetHireDates = new ArrayList<>(MAX_ACCRUAL_MONTH);
        for (int n = 1; n <= MAX_ACCRUAL_MONTH; n++) {
            targetHireDates.add(today.minusMonths(n));
        }

        /* IN 쿼리 1회로 전체 대상 사원 벌크 조회 */
        List<Employee> allTargets = employeeRepository
                .findByCompany_CompanyIdAndEmpHireDateInAndEmpStatusInAndDeleteAtIsNull(
                        companyId, targetHireDates,
                        List.of(EmpStatus.ACTIVE, EmpStatus.ON_LEAVE));
        if (allTargets.isEmpty()) return;

        /* hireDate 기준 그룹핑 - 같은 날짜에 여러 사원이 입사한 경우 대비 */
        Map<LocalDate, List<Employee>> byHireDate = allTargets.stream()
                .collect(Collectors.groupingBy(Employee::getEmpHireDate));

        /* n별 처리 - 기존 호출 시그니처(accrueIfEligible 에 n 전달) 유지 */
        for (int n = 1; n <= MAX_ACCRUAL_MONTH; n++) {
            LocalDate targetHireDate = today.minusMonths(n);
            List<Employee> targets = byHireDate.getOrDefault(targetHireDate, List.of());
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
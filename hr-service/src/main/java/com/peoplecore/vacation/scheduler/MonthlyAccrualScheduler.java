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
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/* 월차 자동 적립 본체 — 회사 순회 + 사원 단위 적립 위임 */
/* 정기 fire = MonthlyAccrualJob (Quartz) → run() 호출 */
/* 수동 트리거 = 관리 API → run() 호출 (장애 복구 시 운영자 직접 실행) */
/* 사원 단위 처리는 MonthlyAccrualService 로 위임 (REQUIRES_NEW 격리) */
/* 멀티 노드 한 대만 fire = QRTZ_LOCKS row lock 보장 (Redis 분산락 불필요) */
@Component
@Slf4j
public class MonthlyAccrualScheduler {

    /* 월차 적립 대상 개월차 - 입사 1~11개월차. 12개월(1년)은 AnnualTransitionScheduler 담당 */
    private static final int MAX_ACCRUAL_MONTH = 11;

    /* KST 고정 - 서버 로컬 타임존과 무관하게 정책 시각 준수 */
    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final CompanyRepository companyRepository;
    private final VacationTypeRepository vacationTypeRepository;
    private final EmployeeRepository employeeRepository;
    private final MonthlyAccrualService monthlyAccrualService;

    @Autowired
    public MonthlyAccrualScheduler(CompanyRepository companyRepository,
                                   VacationTypeRepository vacationTypeRepository,
                                   EmployeeRepository employeeRepository,
                                   MonthlyAccrualService monthlyAccrualService) {
        this.companyRepository = companyRepository;
        this.vacationTypeRepository = vacationTypeRepository;
        this.employeeRepository = employeeRepository;
        this.monthlyAccrualService = monthlyAccrualService;
    }

    /* 정기/수동 공용 진입점. 두 경로 모두 같은 메서드 통과 → 동작 일관성 보장 */
    public void run() {
        LocalDate today = LocalDate.now(ZONE_SEOUL);
        log.info("[MonthlyAccrual] 시작 - date={}", today);

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
        log.info("[MonthlyAccrual] 완료 - date={}, companies={}/{}",
                today, processedCompanies, activeCompanies.size());
    }

    /* 회사 단위 처리 - MONTHLY 유형 조회 + 1~11개월차 대상 IN 쿼리 1회로 벌크 조회 */
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

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
import com.peoplecore.vacation.service.AnnualTransitionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/* 월차→연차 전환 본체 — 1주년 도달 사원 처리 */
/* 정기 fire = AnnualTransitionJob (Quartz) → run() 호출 */
/* 수동 트리거 = 관리 API → run() 호출 */
/* HIRE: 월차 소멸 + 1년차 연차 발생 / FISCAL: 월차 소멸만 */
/* 멀티 노드 한 대만 fire = QRTZ_LOCKS row lock 보장 (Redis 분산락 불필요) */
@Component
@Slf4j
public class AnnualTransitionScheduler {

    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final CompanyRepository companyRepository;
    private final VacationPolicyRepository vacationPolicyRepository;
    private final VacationTypeRepository vacationTypeRepository;
    private final EmployeeRepository employeeRepository;
    private final AnnualTransitionService annualTransitionService;

    @Autowired
    public AnnualTransitionScheduler(CompanyRepository companyRepository,
                                     VacationPolicyRepository vacationPolicyRepository,
                                     VacationTypeRepository vacationTypeRepository,
                                     EmployeeRepository employeeRepository,
                                     AnnualTransitionService annualTransitionService) {
        this.companyRepository = companyRepository;
        this.vacationPolicyRepository = vacationPolicyRepository;
        this.vacationTypeRepository = vacationTypeRepository;
        this.employeeRepository = employeeRepository;
        this.annualTransitionService = annualTransitionService;
    }

    /* 정기/수동 공용 진입점 */
    public void run() {
        LocalDate today = LocalDate.now(ZONE_SEOUL);
        log.info("[AnnualTransition] 시작 - date={}", today);

        List<Company> activeCompanies = companyRepository.findByCompanyStatus(CompanyStatus.ACTIVE);
        int processed = 0;
        for (Company company : activeCompanies) {
            try {
                processCompany(company, today);
                processed++;
            } catch (Exception e) {
                log.error("[AnnualTransition] 회사 처리 실패 - companyId={}, err={}",
                        company.getCompanyId(), e.getMessage(), e);
            }
        }
        log.info("[AnnualTransition] 완료 - date={}, companies={}/{}", today, processed, activeCompanies.size());
    }

    /* 회사 단위 - 정책/유형 조회 후 1주년 도달 사원 처리 */
    private void processCompany(Company company, LocalDate today) {
        UUID companyId = company.getCompanyId();

        VacationPolicy policy = vacationPolicyRepository.findByCompanyIdFetchRules(companyId).orElse(null);
        if (policy == null) {
            log.warn("[AnnualTransition] 정책 없음 - companyId={}", companyId);
            return;
        }

        VacationType monthlyType = vacationTypeRepository
                .findByCompanyIdAndTypeCode(companyId, VacationType.CODE_MONTHLY).orElse(null);
        VacationType annualType = vacationTypeRepository
                .findByCompanyIdAndTypeCode(companyId, VacationType.CODE_ANNUAL).orElse(null);
        if (monthlyType == null || annualType == null) {
            log.warn("[AnnualTransition] 시스템 유형 누락 - companyId={}, monthly={}, annual={}",
                    companyId, monthlyType != null, annualType != null);
            return;
        }

        /* 1주년 도달 = today.minusYears(1) == empHireDate */
        LocalDate oneYearAgoHireDate = today.minusYears(1);
        List<Employee> emps = employeeRepository
                .findByCompany_CompanyIdAndEmpHireDateAndEmpStatusInAndDeleteAtIsNull(
                        companyId, oneYearAgoHireDate,
                        List.of(EmpStatus.ACTIVE, EmpStatus.ON_LEAVE));

        if (emps.isEmpty()) return;
        log.info("[AnnualTransition] companyId={}, 대상={}명", companyId, emps.size());

        for (Employee emp : emps) {
            try {
                annualTransitionService.transition(companyId, emp, policy, monthlyType, annualType, today);
            } catch (Exception e) {
                log.error("[AnnualTransition] 사원 처리 실패 - empId={}, err={}",
                        emp.getEmpId(), e.getMessage(), e);
            }
        }
    }
}

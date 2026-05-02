package com.peoplecore.vacation.scheduler;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.domain.CompanyStatus;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.employee.domain.EmpGender;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationTypeRepository;
import com.peoplecore.vacation.service.MenstrualMonthlyGrantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/* 생리휴가 월별 자동 적립 본체 — 매월 1일 처리 */
/* 정기 fire = MenstrualMonthlyGrantJob (Quartz) → run() 호출 */
/* 수동 트리거 = 관리 API → run() 호출 */
/* 사원 단위 처리는 MenstrualMonthlyGrantService 로 위임 (REQUIRES_NEW 격리) */
/* 대상: 회사 ACTIVE + 사원 FEMALE + ACTIVE + not deleted */
/* 멀티 노드 한 대만 fire = QRTZ_LOCKS row lock 보장 (Redis 분산락 불필요) */
@Component
@Slf4j
public class MenstrualMonthlyGrantScheduler {

    /* KST 고정 - 서버 로컬 타임존과 무관하게 정책 시각 준수 */
    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final CompanyRepository companyRepository;
    private final VacationTypeRepository vacationTypeRepository;
    private final EmployeeRepository employeeRepository;
    private final MenstrualMonthlyGrantService menstrualMonthlyGrantService;

    @Autowired
    public MenstrualMonthlyGrantScheduler(CompanyRepository companyRepository,
                                          VacationTypeRepository vacationTypeRepository,
                                          EmployeeRepository employeeRepository,
                                          MenstrualMonthlyGrantService menstrualMonthlyGrantService) {
        this.companyRepository = companyRepository;
        this.vacationTypeRepository = vacationTypeRepository;
        this.employeeRepository = employeeRepository;
        this.menstrualMonthlyGrantService = menstrualMonthlyGrantService;
    }

    /* 정기/수동 공용 진입점 — 여성 사원 전체 생리휴가 월 적립 + 전월 미사용 만료 */
    public void run() {
        LocalDate today = LocalDate.now(ZONE_SEOUL);
        log.info("[MenstrualGrant] 시작 - date={}", today);

        List<Company> activeCompanies = companyRepository.findByCompanyStatus(CompanyStatus.ACTIVE);
        int processedCompanies = 0;
        for (Company company : activeCompanies) {
            try {
                processCompany(company, today);
                processedCompanies++;
            } catch (Exception e) {
                log.error("[MenstrualGrant] 회사 처리 실패 - companyId={}, err={}",
                        company.getCompanyId(), e.getMessage(), e);
            }
        }
        log.info("[MenstrualGrant] 완료 - date={}, companies={}/{}",
                today, processedCompanies, activeCompanies.size());
    }

    /* 회사 단위 처리 - MENSTRUAL 유형 조회 + 여성 ACTIVE 사원 순회 */
    /* MENSTRUAL 유형이 없으면 (initDefault 누락 회사) 경고 로그만 찍고 skip */
    private void processCompany(Company company, LocalDate today) {
        UUID companyId = company.getCompanyId();
        VacationType menstrualType = vacationTypeRepository
                .findByCompanyIdAndTypeCode(companyId, "MENSTRUAL")
                .orElse(null);
        if (menstrualType == null) {
            log.warn("[MenstrualGrant] MENSTRUAL 유형 없음 - companyId={} (initDefault 재실행 필요)", companyId);
            return;
        }
        if (!Boolean.TRUE.equals(menstrualType.getIsActive())) {
            log.info("[MenstrualGrant] MENSTRUAL 유형 비활성 - companyId={}, skip", companyId);
            return;
        }

        // 여성 + ACTIVE + not deleted - 휴직자/퇴사자 제외
        List<Employee> targets = employeeRepository
                .findByCompany_CompanyIdAndEmpGenderAndEmpStatusAndDeleteAtIsNull(
                        companyId, EmpGender.FEMALE, EmpStatus.ACTIVE);
        if (targets.isEmpty()) {
            log.info("[MenstrualGrant] 대상 사원 없음 - companyId={}", companyId);
            return;
        }

        for (Employee emp : targets) {
            try {
                menstrualMonthlyGrantService.grantForEmployee(companyId, emp, menstrualType, today);
            } catch (Exception e) {
                log.error("[MenstrualGrant] 사원 처리 실패 - companyId={}, empId={}, err={}",
                        companyId, emp.getEmpId(), e.getMessage(), e);
            }
        }
        log.info("[MenstrualGrant] 회사 처리 완료 - companyId={}, targets={}", companyId, targets.size());
    }
}

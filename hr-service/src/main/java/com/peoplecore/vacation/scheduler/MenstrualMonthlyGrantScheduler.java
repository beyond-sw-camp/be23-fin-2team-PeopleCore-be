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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/* 생리휴가 월별 자동 적립 스케줄러 - 매월 1일 00:05 KST 실행 */
/* 분산 락으로 멀티 인스턴스 중복 실행 방지 (락 키: menstrual-monthly-grant:{yyyy-MM}) */
/* 사원 단위 처리는 MenstrualMonthlyGrantService 로 위임 (REQUIRES_NEW 격리) */
/* 대상: 회사 ACTIVE + 사원 FEMALE + ACTIVE + not deleted */
@Component
@Slf4j
public class MenstrualMonthlyGrantScheduler {

    /* 분산 락 TTL - 월별 1회 실행이라 여유있게 설정 */
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    /* 락 키 prefix - 년월 기준 */
    private static final String LOCK_KEY_PREFIX = "menstrual-monthly-grant";

    /* KST 고정 - 서버 로컬 타임존과 무관하게 정책 시각 준수 */
    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final StringRedisTemplate redisTemplate;
    private final CompanyRepository companyRepository;
    private final VacationTypeRepository vacationTypeRepository;
    private final EmployeeRepository employeeRepository;
    private final MenstrualMonthlyGrantService menstrualMonthlyGrantService;

    @Autowired
    public MenstrualMonthlyGrantScheduler(StringRedisTemplate redisTemplate,
                                          CompanyRepository companyRepository,
                                          VacationTypeRepository vacationTypeRepository,
                                          EmployeeRepository employeeRepository,
                                          MenstrualMonthlyGrantService menstrualMonthlyGrantService) {
        this.redisTemplate = redisTemplate;
        this.companyRepository = companyRepository;
        this.vacationTypeRepository = vacationTypeRepository;
        this.employeeRepository = employeeRepository;
        this.menstrualMonthlyGrantService = menstrualMonthlyGrantService;
    }

    /* 매월 1일 00:05 KST 실행 - 여성 사원 전체 생리휴가 월 적립 + 전월 미사용 만료 */
    /* cron: 초 분 시 일 월 요일 */
    @Scheduled(cron = "0 5 0 1 * *", zone = "Asia/Seoul")
    public void run() {
        LocalDate today = LocalDate.now(ZONE_SEOUL);
        // 년월 기준 락 키 - 같은 달 중복 실행 방지
        String lockKey = LOCK_KEY_PREFIX + ":" + today.getYear() + "-" + today.getMonthValue();

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("[MenstrualGrant] 다른 인스턴스 진행 중 - skip. date={}", today);
            return;
        }
        log.info("[MenstrualGrant] 시작 - date={}", today);
        try {
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
        } finally {
            // 스케줄러 종료 즉시 해제 - 다음 달 재실행 필요 시 락 빠르게 반환
            redisTemplate.delete(lockKey);
        }
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

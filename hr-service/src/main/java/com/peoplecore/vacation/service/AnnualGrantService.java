package com.peoplecore.vacation.service;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.entity.VacationBalance;
import com.peoplecore.vacation.entity.VacationGrantRule;
import com.peoplecore.vacation.entity.VacationLedger;
import com.peoplecore.vacation.entity.VacationPolicy;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationBalanceRepository;
import com.peoplecore.vacation.repository.VacationGrantRuleRepository;
import com.peoplecore.vacation.repository.VacationLedgerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/* 연차 발생 서비스 - HIRE / FISCAL 정책별 분기 */
/* 사원 단위 REQUIRES_NEW 트랜잭션 격리 (스케줄러가 try-catch 로 흡수) */
@Service
@Slf4j
public class AnnualGrantService {

    /* 비례 계산 소수점 자리수 - 6자리까지 ratio 유지 후 최종 grantDays 는 1자리 반올림 */
    private static final int RATIO_SCALE = 6;
    private static final int GRANT_DAYS_SCALE = 1;

    /* FISCAL 첫해 비례의 기준 규칙 근속연수 - 근속 1년차 grantDays (통상 15일) 를 baseline 으로 */
    private static final int FISCAL_FIRST_YEAR_RULE_YEARS = 1;

    private final VacationBalanceRepository vacationBalanceRepository;
    private final VacationLedgerRepository vacationLedgerRepository;
    private final VacationGrantRuleRepository vacationGrantRuleRepository;
    private final AttendanceCheckService attendanceCheckService;
    private final BusinessDayCalculator businessDayCalculator;

    @Autowired
    public AnnualGrantService(VacationBalanceRepository vacationBalanceRepository,
                              VacationLedgerRepository vacationLedgerRepository,
                              VacationGrantRuleRepository vacationGrantRuleRepository,
                              AttendanceCheckService attendanceCheckService,
                              BusinessDayCalculator businessDayCalculator) {
        this.vacationBalanceRepository = vacationBalanceRepository;
        this.vacationLedgerRepository = vacationLedgerRepository;
        this.vacationGrantRuleRepository = vacationGrantRuleRepository;
        this.attendanceCheckService = attendanceCheckService;
        this.businessDayCalculator = businessDayCalculator;
    }

    /* HIRE 정책 - 입사기념일 도래 사원 처리. N>=2 가정 (1주년은 AnnualTransition 담당) */
    /* 근속연수 매칭 규칙의 grantDays 전액 부여 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void grantForHire(UUID companyId, Employee emp, VacationType annualType,
                             VacationPolicy policy, int yearsOfService, LocalDate today) {
        VacationGrantRule rule = vacationGrantRuleRepository
                .findMatchingRule(policy.getPolicyId(), yearsOfService)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_RULE_NOT_FOUND));

        BigDecimal grantDays = BigDecimal.valueOf(rule.getGrantDays());
        grantAndRecord(companyId, emp, annualType, today, grantDays, "HIRE 근속 " + yearsOfService + "년");
    }

    /* FISCAL 정책 - 회계연도 시작일 당일 전 사원 처리 */
    /* 첫 회계연도 입사자 (이전 fiscal start 이후 입사) 는 덮은 영업일 / 전체 영업일 비례 */
    /* 그 외는 근속 N년 매칭 규칙 grantDays 전액 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void grantForFiscal(UUID companyId, Employee emp, VacationType annualType,
                               VacationPolicy policy, LocalDate today) {
        LocalDate hireDate = emp.getEmpHireDate();
        if (hireDate == null) {
            log.warn("[AnnualGrant-FISCAL] empHireDate null - empId={}", emp.getEmpId());
            return;
        }

        /* 미래 입사 (엣지) - 스킵 */
        if (!hireDate.isBefore(today)) {
            log.warn("[AnnualGrant-FISCAL] hireDate >= today - empId={}, hireDate={}, today={}",
                    emp.getEmpId(), hireDate, today);
            return;
        }

        LocalDate previousFiscalStart = today.minusYears(1);

        /* 이전 fiscal start 이후 ~ today 이전 입사 = 첫 회계연도 입사자 */
        boolean isFirstFiscalYear = hireDate.isAfter(previousFiscalStart);
        if (isFirstFiscalYear) {
            grantForFiscalFirstYear(companyId, emp, annualType, policy, today, hireDate, previousFiscalStart);
        } else {
            int yearsOfService = (int) ChronoUnit.YEARS.between(hireDate, today);
            if (yearsOfService < 1) return;
            grantForFiscalNormal(companyId, emp, annualType, policy, yearsOfService, today);
        }
    }

    /* FISCAL 첫 회계연도 입사자 비례 부여 - (덮은 영업일 / 이전 회계연도 전체 영업일) × 1년차 grantDays */
    private void grantForFiscalFirstYear(UUID companyId, Employee emp, VacationType annualType,
                                         VacationPolicy policy, LocalDate today,
                                         LocalDate hireDate, LocalDate previousFiscalStart) {
        LocalDate periodEnd = today.minusDays(1);

        int coveredDays = attendanceCheckService.countCoveredBusinessDays(
                companyId, emp.getEmpId(), hireDate, periodEnd);
        int totalBizDays = businessDayCalculator.countBusinessDays(
                companyId, previousFiscalStart, periodEnd);

        if (totalBizDays == 0) {
            log.warn("[AnnualGrant-FISCAL-FIRST] totalBizDays=0 - empId={}, period={}~{}",
                    emp.getEmpId(), previousFiscalStart, periodEnd);
            return;
        }

        VacationGrantRule baseRule = vacationGrantRuleRepository
                .findMatchingRule(policy.getPolicyId(), FISCAL_FIRST_YEAR_RULE_YEARS)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_RULE_NOT_FOUND));

        BigDecimal ratio = BigDecimal.valueOf(coveredDays)
                .divide(BigDecimal.valueOf(totalBizDays), RATIO_SCALE, RoundingMode.HALF_UP);
        BigDecimal grantDays = BigDecimal.valueOf(baseRule.getGrantDays())
                .multiply(ratio)
                .setScale(GRANT_DAYS_SCALE, RoundingMode.HALF_UP);

        if (grantDays.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("[AnnualGrant-FISCAL-FIRST] 비례 결과 0 - empId={}, covered={}, total={}",
                    emp.getEmpId(), coveredDays, totalBizDays);
            return;
        }

        log.info("[AnnualGrant-FISCAL-FIRST] empId={}, hireDate={}, covered={}/{}={}, grantDays={}",
                emp.getEmpId(), hireDate, coveredDays, totalBizDays, ratio, grantDays);

        grantAndRecord(companyId, emp, annualType, today, grantDays,
                "FISCAL 첫해 비례 (" + coveredDays + "/" + totalBizDays + ")");
    }

    /* FISCAL 정상 회계연도 - 근속 N년 매칭 grantDays 전액 */
    private void grantForFiscalNormal(UUID companyId, Employee emp, VacationType annualType,
                                      VacationPolicy policy, int yearsOfService, LocalDate today) {
        VacationGrantRule rule = vacationGrantRuleRepository
                .findMatchingRule(policy.getPolicyId(), yearsOfService)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_RULE_NOT_FOUND));

        BigDecimal grantDays = BigDecimal.valueOf(rule.getGrantDays());
        grantAndRecord(companyId, emp, annualType, today, grantDays,
                "FISCAL 근속 " + yearsOfService + "년");
    }

    /* 공통 - Balance createNew 또는 기존 + accrue + INITIAL_GRANT Ledger */
    /* balance_year = today.getYear(), expires_at = today + 1년 - 1일 (만료 잡이 사용) */
    private void grantAndRecord(UUID companyId, Employee emp, VacationType annualType,
                                LocalDate today, BigDecimal grantDays, String reason) {
        Integer balanceYear = today.getYear();
        LocalDate expiresAt = today.plusYears(1).minusDays(1);

        VacationBalance balance = vacationBalanceRepository
                .findOne(companyId, emp.getEmpId(), annualType.getTypeId(), balanceYear)
                .orElseGet(() -> vacationBalanceRepository.save(
                        VacationBalance.createNew(
                                companyId, annualType, emp, balanceYear, today, expiresAt)));

        BigDecimal before = balance.getTotalDays();
        balance.accrue(grantDays,grantDays);
        BigDecimal after = balance.getTotalDays();
        vacationLedgerRepository.save(VacationLedger.ofInitialGrant(balance, grantDays, before, after));

        log.info("[AnnualGrant] 연차 발생 - empId={}, days={}, total={}, reason={}",
                emp.getEmpId(), grantDays, after, reason);
    }
}
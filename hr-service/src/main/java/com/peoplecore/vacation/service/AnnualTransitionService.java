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
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/* 1년 도달 월차 → 연차 전환 서비스 - 사원 단위 REQUIRES_NEW 격리 */
/* 공통: 월차 잔여 전부 expireRemaining + ledger.ofExpired */
/* HIRE: 1년차 연차 발생 + ledger.ofInitialGrant + ledger.ofAnnualTransition */
/* FISCAL: 월차 소멸만 (연차는 AnnualGrantScheduler 가 회계연도 시작일 담당) */
@Service
@Slf4j
public class AnnualTransitionService {

    /* 1년차 연차 규칙 매칭 근속연수 */
    private static final int FIRST_YEAR_RULE_YEARS = 1;

    private final VacationBalanceRepository vacationBalanceRepository;
    private final VacationLedgerRepository vacationLedgerRepository;
    private final VacationGrantRuleRepository vacationGrantRuleRepository;

    @Autowired
    public AnnualTransitionService(VacationBalanceRepository vacationBalanceRepository,
                                   VacationLedgerRepository vacationLedgerRepository,
                                   VacationGrantRuleRepository vacationGrantRuleRepository) {
        this.vacationBalanceRepository = vacationBalanceRepository;
        this.vacationLedgerRepository = vacationLedgerRepository;
        this.vacationGrantRuleRepository = vacationGrantRuleRepository;
    }

    /* 사원 1명 전환 처리 */
    /* 월차 소멸 → (HIRE 면 연차 발생 + 전환 표식). 정책별 분기는 내부 if */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transition(UUID companyId, Employee emp, VacationPolicy policy,
                           VacationType monthlyType, VacationType annualType, LocalDate today) {
        LocalDate hireDate = emp.getEmpHireDate();
        if (hireDate == null) {
            log.warn("[AnnualTransition] empHireDate null - empId={}", emp.getEmpId());
            return;
        }

        /* 1. 월차 잔여 소멸 - hireDate.year ~ today.year 순회 (최대 2~3 year) */
        BigDecimal totalExpired = expireMonthlyBalances(companyId, emp, monthlyType, hireDate, today);

        /* 2. HIRE 정책 - 1년차 연차 신규 발생 */
        if (policy.getPolicyBaseType() == VacationPolicy.PolicyBaseType.HIRE) {
            grantFirstYearAnnual(companyId, emp, policy, annualType, today, totalExpired);
        } else {
            log.info("[AnnualTransition] FISCAL 정책 - 월차 소멸만 수행. empId={}, expired={}",
                    emp.getEmpId(), totalExpired);
        }
    }

    /* 월차 balance 전체 year 순회하며 expireRemaining + ofExpired. 소멸 총량 반환 */
    private BigDecimal expireMonthlyBalances(UUID companyId, Employee emp, VacationType monthlyType,
                                              LocalDate hireDate, LocalDate today) {
        BigDecimal totalExpired = BigDecimal.ZERO;
        int startYear = hireDate.getYear();
        int endYear = today.getYear();

        for (int y = startYear; y <= endYear; y++) {
            Optional<VacationBalance> opt = vacationBalanceRepository
                    .findOne(companyId, emp.getEmpId(), monthlyType.getTypeId(), y);
            if (opt.isEmpty()) continue;

            VacationBalance mb = opt.get();
            BigDecimal before = mb.getTotalDays();
            BigDecimal expired = mb.expireRemaining();
            if (expired.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal after = mb.getTotalDays();

            vacationLedgerRepository.save(VacationLedger.ofExpired(
                    mb, expired, before, after, "1년 도달 월차 소멸"));
            totalExpired = totalExpired.add(expired);

            log.info("[AnnualTransition] 월차 소멸 - empId={}, year={}, expired={}",
                    emp.getEmpId(), y, expired);
        }
        return totalExpired;
    }

    /* HIRE 정책 1년차 연차 발생 - 1년차 규칙 grantDays 전액 + InitialGrant + AnnualTransition 표식 */
    private void grantFirstYearAnnual(UUID companyId, Employee emp, VacationPolicy policy,
                                       VacationType annualType, LocalDate today, BigDecimal expiredMonthlyDays) {
        VacationGrantRule rule = vacationGrantRuleRepository
                .findMatchingRule(policy.getPolicyId(), FIRST_YEAR_RULE_YEARS)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_RULE_NOT_FOUND));

        BigDecimal grantDays = BigDecimal.valueOf(rule.getGrantDays());

        Integer balanceYear = today.getYear();
        LocalDate expiresAt = today.plusYears(1).minusDays(1);

        VacationBalance annualBalance = vacationBalanceRepository
                .findOne(companyId, emp.getEmpId(), annualType.getTypeId(), balanceYear)
                .orElseGet(() -> vacationBalanceRepository.save(
                        VacationBalance.createNew(
                                companyId, annualType, emp, balanceYear, today, expiresAt)));

        BigDecimal before = annualBalance.getTotalDays();
        annualBalance.accrue(grantDays);
        BigDecimal after = annualBalance.getTotalDays();

        /* InitialGrant - 연차 신규 발생 기록 */
        vacationLedgerRepository.save(VacationLedger.ofInitialGrant(
                annualBalance, grantDays, before, after));

        /* AnnualTransition - 전환 표식 (change_days = 소멸된 월차량, 음수 부호는 팩토리가 처리) */
        /* before/after 는 accrue 후 동일 시점 값 (전환 표식은 잔여 변동 아님, 표식용) */
        if (expiredMonthlyDays.compareTo(BigDecimal.ZERO) > 0) {
            vacationLedgerRepository.save(VacationLedger.ofAnnualTransition(
                    annualBalance, expiredMonthlyDays, after, after));
        }

        log.info("[AnnualTransition] HIRE 1년차 연차 발생 - empId={}, grantDays={}, expired={}, total={}",
                emp.getEmpId(), grantDays, expiredMonthlyDays, after);
    }
}
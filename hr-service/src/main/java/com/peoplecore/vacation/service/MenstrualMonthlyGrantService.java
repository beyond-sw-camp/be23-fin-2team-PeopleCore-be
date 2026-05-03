package com.peoplecore.vacation.service;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.vacation.entity.VacationBalance;
import com.peoplecore.vacation.entity.VacationLedger;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationBalanceRepository;
import com.peoplecore.vacation.repository.VacationLedgerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

/* 생리휴가 월별 부여 서비스 - 사원 단위 트랜잭션(REQUIRES_NEW) */
/* 매월 1일 스케줄러가 여성 ACTIVE 사원 루프 안에서 호출. 한 사원 실패가 다른 사원에 영향 없도록 격리 */
/* 동작: (1) 전월 미사용 잔여 만료 → expireRemaining  (2) 이번달 1일 적립 → accrue(1) */
/* 연 경계(1월 1일 실행) 시: 전년 Dec 잔여는 전년 row 에서 만료, 당해 row 신규 생성 */
@Service
@Slf4j
public class MenstrualMonthlyGrantService {

    /* 생리휴가 월 부여량 - 법정 1일 고정 */
    private static final BigDecimal MONTHLY_GRANT_DAYS = BigDecimal.ONE;

    private final VacationBalanceRepository vacationBalanceRepository;
    private final VacationLedgerRepository vacationLedgerRepository;

    @Autowired
    public MenstrualMonthlyGrantService(VacationBalanceRepository vacationBalanceRepository,
                                        VacationLedgerRepository vacationLedgerRepository) {
        this.vacationBalanceRepository = vacationBalanceRepository;
        this.vacationLedgerRepository = vacationLedgerRepository;
    }

    /* 사원 1명 생리휴가 월 부여 처리 */
    /* 호출 선조건: 사원이 FEMALE + ACTIVE + not deleted 상태 (스케줄러가 필터 완료) */
    /* 멱등 가드: 동월 ACCRUAL 이력 있으면 전체 skip — cron(매월 1일) + 관리자 수동 트리거 다회 호출 방지 */
    /*           (mid-month 재실행 시 만료+적립 페어가 ledger 에 무한 누적되고 expired_days 부풀던 버그 차단) */
    /* 예외: accrue 내부 cap 초과 시 VACATION_BALANCE_CAP_EXCEEDED (생리는 cap=null 이므로 미발생) */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void grantForEmployee(UUID companyId, Employee emp, VacationType menstrualType, LocalDate today) {
        Long empId = emp.getEmpId();
        Long typeId = menstrualType.getTypeId();
        int currentYear = today.getYear();

        // 전월 식별: 1월 실행 시 전년 12월 (prevYear=currentYear-1), 그 외는 동일 연도
        int prevMonth = today.getMonthValue() - 1;
        int prevYear = (prevMonth == 0) ? currentYear - 1 : currentYear;

        // 당해 row 선조회 — 멱등 가드 판정 + 본 로직 재사용 (조회 1회로 절약)
        VacationBalance balance = vacationBalanceRepository
                .findOne(companyId, empId, typeId, currentYear)
                .orElse(null);

        // 멱등 가드: 당해 row 가 이미 있고 이번달 ACCRUAL 이력 존재 → 전체 skip
        // 첫 호출(row 없음)이면 가드 패스 — 정상 처리 후 ACCRUAL 기록 → 다음 호출부터 가드 작동
        if (balance != null) {
            LocalDate firstDayOfMonth = today.withDayOfMonth(1);
            LocalDateTime monthStart = firstDayOfMonth.atStartOfDay();
            LocalDateTime nextMonthStart = firstDayOfMonth.plusMonths(1).atStartOfDay();
            if (vacationLedgerRepository.existsAccrualInMonth(
                    companyId, balance.getBalanceId(), monthStart, nextMonthStart)) {
                log.info("[MenstrualGrant] 동월 적립 이력 존재 - skip empId={}, year={}, month={}",
                        empId, currentYear, today.getMonthValue());
                return;
            }
        }

        // 연 경계 만료: 전년 row 가 존재하면 Dec 잔여를 만료 처리 (당해 row 와 분리된 식별자)
        if (prevYear < currentYear) {
            vacationBalanceRepository.findOne(companyId, empId, typeId, prevYear)
                    .ifPresent(prevBal -> expireAndLog(prevBal, "생리휴가 전년 Dec 미사용 만료"));
        }

        // 당해 row 없으면 신규 생성 (첫 실행 / 신규 입사자 대비)
        if (balance == null) {
            balance = createAndLogInitial(companyId, emp, menstrualType, currentYear, today);
        }

        // 동일 연도 전월 만료: 이미 accrue 된 Balance 의 가용 잔여를 expired 로 이동
        // 가드 통과 = 이번달 ACCRUAL 없음 = 직전 ACCRUAL 은 전월 이전 → 만료 대상이 맞음
        if (prevYear == currentYear) {
            expireAndLog(balance, "생리휴가 전월 미사용 만료");
        }

        // 만료일을 이번달 말일로 갱신 - BalanceExpiry 잡이 신규 적립분을 전월 만료일로 잘못 소멸시키지 않도록
        balance.updateExpiresAt(today.with(TemporalAdjusters.lastDayOfMonth()));

        // 이번 달 1일 적립 - cap 없음
        BigDecimal before = balance.getTotalDays();
        balance.accrue(MONTHLY_GRANT_DAYS, null);
        BigDecimal after = balance.getTotalDays();
        vacationLedgerRepository.save(VacationLedger.ofMenstrualAccrual(balance, MONTHLY_GRANT_DAYS, before, after));

        log.info("[MenstrualGrant] 적립 - empId={}, year={}, month={}, total={}",
                empId, currentYear, today.getMonthValue(), after);
    }

    /* 당해 연도 첫 row 생성 + INITIAL_GRANT 로그 기록 */
    /* expiresAt = 지급월 말일 - BalanceExpiry 잡과 이중 가드 (스케줄러 expireRemaining 누락 시에도 안전) */
    private VacationBalance createAndLogInitial(UUID companyId, Employee emp, VacationType type,
                                                int year, LocalDate today) {
        LocalDate expiresAt = today.with(TemporalAdjusters.lastDayOfMonth());
        VacationBalance newBal = vacationBalanceRepository.save(
                VacationBalance.createNew(companyId, type, emp, year, today, expiresAt));
        log.info("[MenstrualGrant] 신규 Balance 생성 - empId={}, year={}, expiresAt={}",
                emp.getEmpId(), year, expiresAt);
        return newBal;
    }

    /* 잔여 만료 + EXPIRED 로그 기록 - 가용 0 이면 no-op */
    private void expireAndLog(VacationBalance balance, String reason) {
        BigDecimal remaining = balance.getAvailableDays();
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) return;

        BigDecimal before = balance.getTotalDays();
        BigDecimal expired = balance.expireRemaining();
        BigDecimal after = balance.getTotalDays(); // total 은 변하지 않음 (expired 컬럼만 증가)
        vacationLedgerRepository.save(VacationLedger.ofExpired(balance, expired, before, after, reason));
        log.info("[MenstrualGrant] 만료 - empId={}, year={}, expired={}",
                balance.getEmployee().getEmpId(), balance.getBalanceYear(), expired);
    }
}

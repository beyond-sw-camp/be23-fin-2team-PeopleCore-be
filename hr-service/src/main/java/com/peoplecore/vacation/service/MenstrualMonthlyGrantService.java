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
    /* 예외: accrue 내부 cap 초과 시 VACATION_BALANCE_CAP_EXCEEDED (생리는 cap=null 이므로 미발생) */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void grantForEmployee(UUID companyId, Employee emp, VacationType menstrualType, LocalDate today) {
        Long empId = emp.getEmpId();
        Long typeId = menstrualType.getTypeId();
        int currentYear = today.getYear();

        // 전월 식별: 1월 실행 시 전년 12월 (prevYear=currentYear-1), 그 외는 동일 연도
        int prevMonth = today.getMonthValue() - 1;
        int prevYear = (prevMonth == 0) ? currentYear - 1 : currentYear;

        // 연 경계 만료: 전년 row 가 존재하면 Dec 잔여를 만료 처리 (당해 row 와 분리된 식별자)
        if (prevYear < currentYear) {
            vacationBalanceRepository.findOne(companyId, empId, typeId, prevYear)
                    .ifPresent(prevBal -> expireAndLog(prevBal, "생리휴가 전년 Dec 미사용 만료"));
        }

        // 당해 연도 row 조회 또는 신규 생성 (첫 실행 / 신규 입사자 대비)
        VacationBalance balance = vacationBalanceRepository
                .findOne(companyId, empId, typeId, currentYear)
                .orElseGet(() -> createAndLogInitial(companyId, emp, menstrualType, currentYear, today));

        // 동일 연도 전월 만료: 이미 accrue 된 Balance 의 가용 잔여를 expired 로 이동
        // (첫 생성 직후 available=0 이라 안전 - 아무 일도 일어나지 않음)
        if (prevYear == currentYear) {
            expireAndLog(balance, "생리휴가 전월 미사용 만료");
        }

        // 이번 달 1일 적립 - cap 없음
        BigDecimal before = balance.getTotalDays();
        balance.accrue(MONTHLY_GRANT_DAYS, null);
        BigDecimal after = balance.getTotalDays();
        vacationLedgerRepository.save(VacationLedger.ofMenstrualAccrual(balance, MONTHLY_GRANT_DAYS, before, after));

        log.info("[MenstrualGrant] 적립 - empId={}, year={}, month={}, total={}",
                empId, currentYear, today.getMonthValue(), after);
    }

    /* 당해 연도 첫 row 생성 + INITIAL_GRANT 로그 기록 */
    /* expiresAt=null: 월 만료는 스케줄러가 expireRemaining 으로 직접 처리하므로 row 레벨 만료일 불필요 */
    private VacationBalance createAndLogInitial(UUID companyId, Employee emp, VacationType type,
                                                int year, LocalDate today) {
        VacationBalance newBal = vacationBalanceRepository.save(
                VacationBalance.createNew(companyId, type, emp, year, today, null));
        log.info("[MenstrualGrant] 신규 Balance 생성 - empId={}, year={}", emp.getEmpId(), year);
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

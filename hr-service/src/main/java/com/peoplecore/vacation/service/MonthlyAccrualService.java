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

/* 월차 적립 서비스 - 사원 단위 트랜잭션 (REQUIRES_NEW 격리) */
/* 스케줄러가 for 문 안에서 호출. 한 사원 실패해도 다른 사원 처리 계속 */
@Service
@Slf4j
public class MonthlyAccrualService {

    /* 월차 캡 (근로기준법) - 11일 누적되면 더 적립 안 함. 1년 도달 시 연차 전환 */
    private static final int MONTHLY_CAP_DAYS = 11;

    private final VacationBalanceRepository vacationBalanceRepository;
    private final VacationLedgerRepository vacationLedgerRepository;
    private final AttendanceCheckService attendanceCheckService;

    @Autowired
    public MonthlyAccrualService(VacationBalanceRepository vacationBalanceRepository,
                                 VacationLedgerRepository vacationLedgerRepository,
                                 AttendanceCheckService attendanceCheckService) {
        this.vacationBalanceRepository = vacationBalanceRepository;
        this.vacationLedgerRepository = vacationLedgerRepository;
        this.attendanceCheckService = attendanceCheckService;
    }

    /* 사원 단위 월차 적립 판정 + 수행 */
    /* 실패 예외는 상위로 전파 → 스케줄러가 try-catch 로 흡수 */
    /* REQUIRES_NEW: 한 사원 롤백이 다른 사원 처리에 영향 없도록 격리 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void accrueIfEligible(UUID companyId, Employee emp, VacationType monthlyType,
                                 int monthNth, LocalDate today) {
        /* 만근 판정 기간 - [hireDate + (n-1)개월, hireDate + n개월 - 1일] */
        LocalDate hireDate = emp.getEmpHireDate();
        LocalDate periodStart = hireDate.plusMonths(monthNth - 1L);
        LocalDate periodEnd = hireDate.plusMonths(monthNth).minusDays(1);

        boolean full = attendanceCheckService.isFullAttendance(companyId, emp.getEmpId(), periodStart, periodEnd);
        if (!full) {
            log.info("[MonthlyAccrual] 만근 미충족 - empId={}, monthNth={}, period={}~{}",
                    emp.getEmpId(), monthNth, periodStart, periodEnd);
            return;
        }

        /* 잔여 회기 연도 = 오늘의 달력 연도 (FISCAL 정책은 추후 반영) */
        Integer balanceYear = today.getYear();

        /* Balance 조회 - 없으면 createNew. expires_at = 1년 도달일 (AnnualTransitionScheduler 담당) */
        VacationBalance balance = vacationBalanceRepository
                .findOne(companyId, emp.getEmpId(), monthlyType.getTypeId(), balanceYear)
                .orElseGet(() -> vacationBalanceRepository.save(
                        VacationBalance.createNew(
                                companyId, monthlyType, emp, balanceYear,
                                today, hireDate.plusYears(1))));

        /* 캡 체크 - 이미 11일 이상 적립돼있으면 추가 적립 skip */
        if (balance.getTotalDays().compareTo(BigDecimal.valueOf(MONTHLY_CAP_DAYS)) >= 0) {
            log.info("[MonthlyAccrual] 캡 도달 (>= {}) - empId={}, total={}",
                    MONTHLY_CAP_DAYS, emp.getEmpId(), balance.getTotalDays());
            return;
        }

        /* 1일 적립 + ACCRUAL Ledger 기록 */
        BigDecimal before = balance.getTotalDays();
        /**/
        balance.accrue(BigDecimal.ONE, BigDecimal.valueOf(MONTHLY_CAP_DAYS));
        BigDecimal after = balance.getTotalDays();
        vacationLedgerRepository.save(VacationLedger.ofAccrual(balance, BigDecimal.ONE, before, after));

        log.info("[MonthlyAccrual] 적립 - empId={}, monthNth={}, total={}",
                emp.getEmpId(), monthNth, after);
    }
}
package com.peoplecore.vacation.service;

import com.peoplecore.vacation.entity.VacationBalance;
import com.peoplecore.vacation.entity.VacationLedger;
import com.peoplecore.vacation.repository.VacationLedgerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/* 만료 잡 서비스 - balance 단위 REQUIRES_NEW 격리 */
/* expireRemaining 은 멱등 (available=0 이면 no-op) → 재실행 안전 */
@Service
@Slf4j
public class BalanceExpiryService {

    private final VacationLedgerRepository vacationLedgerRepository;

    @Autowired
    public BalanceExpiryService(VacationLedgerRepository vacationLedgerRepository) {
        this.vacationLedgerRepository = vacationLedgerRepository;
    }

    /* balance 1건 만료 처리 - available 0 이면 스킵 (멱등) */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireBalance(VacationBalance balance) {
        BigDecimal before = balance.getTotalDays();
        BigDecimal expired = balance.expireRemaining();
        if (expired.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("[BalanceExpiry] 이미 처리됨 - balanceId={}, available=0", balance.getBalanceId());
            return;
        }
        BigDecimal after = balance.getTotalDays();

        vacationLedgerRepository.save(VacationLedger.ofExpired(
                balance, expired, before, after, "만료일 도달 소멸"));

        log.info("[BalanceExpiry] 만료 처리 - balanceId={}, empId={}, expired={}, expiresAt={}",
                balance.getBalanceId(),
                balance.getEmployee().getEmpId(),
                expired,
                balance.getExpiresAt());
    }
}
package com.peoplecore.pay.listener;

import com.peoplecore.event.PayrollPaidEvent;
import com.peoplecore.pay.service.PensionDepositService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class PensionDepositEventListener {
//    급여대장 PAID 처리 시 DC 사원의 적립 SCHEDULED 자동 생성
//    PayrollService 트랜잭션 커밋 후 수행

    private final PensionDepositService pensionDepositService;

    @Autowired
    public PensionDepositEventListener(PensionDepositService pensionDepositService) {
        this.pensionDepositService = pensionDepositService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPayrollPaid(PayrollPaidEvent event) {
        try {
            int created = pensionDepositService.createScheduledDeposits(
                    event.getCompanyId(), event.getPayYearMonth());
            log.info("[PensionDeposit] 자동 산정 (SCHEDULED) - runId={}, payYearMonth={}, created={}",
                    event.getPayrollRunId(), event.getPayYearMonth(), created);
        } catch (Exception e) {
            log.error("[PensionDeposit] 자동 산정 실패 - runId={}, error={}",
                    event.getPayrollRunId(), e.getMessage(), e);
            // 자동 산정 실패해도 PAID 트랜잭션은 이미 커밋됨 (AFTER_COMMIT)
            // → 운영자가 [월별 일괄 적립] 버튼으로 수동 보정 가능
        }
    }
}

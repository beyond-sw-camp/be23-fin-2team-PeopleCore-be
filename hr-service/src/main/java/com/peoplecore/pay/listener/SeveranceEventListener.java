package com.peoplecore.pay.listener;

import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.service.SeveranceService;
import com.peoplecore.resign.event.EmployeeRetiredEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class SeveranceEventListener {
//    사원 퇴직 완료시 퇴직금 자동 산정
//    ResignService 트랜잭션 커밋 이후 수행

    private final SeveranceService severanceService;
    @Autowired
    public SeveranceEventListener(SeveranceService severanceService) {
        this.severanceService = severanceService;
    }

    // 연차수당(Order=1) 산정 후 실행 - 퇴직금 평균임금에 연차수당 합산 보장
    @Order(2)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmployeeRetired(EmployeeRetiredEvent event) {
        try {
            severanceService.calculateByEmpId(event.getCompanyId(), event.getEmpId());
            log.info("[Severance] 퇴직금 자동 산정 완료 - empId={}", event.getEmpId());
        } catch (CustomException e){
            // 근속 1년 미만 : 산정 제외
            if(e.getErrorCode() == ErrorCode.SERVICE_PERIOD_TOO_SHORT){
                log.info("[Severance] 근속 1년 미만 - 자동 산정 스킵, empId={}", event.getEmpId());
            } else if (e.getErrorCode() == ErrorCode.RESIGN_DATE_NOT_SET) {
                log.info("[Severance] 퇴직일 미설정으로 산정 붉가, empId={}", event.getEmpId());
            } else {
                log.error("[Severance] 자동 산정 실패 - empId={}, error={}", event.getEmpId(), e.getMessage());
            }
        } catch (Exception e){
            log.error("[Severance] 자동 산정 중 예외 - empId={}", event.getEmpId(), e);
        }
    }
}

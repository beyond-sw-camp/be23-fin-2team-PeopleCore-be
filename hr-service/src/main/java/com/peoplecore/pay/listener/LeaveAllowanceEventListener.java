package com.peoplecore.pay.listener;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.pay.service.LeaveAllowanceService;
import com.peoplecore.resign.domain.Resign;
import com.peoplecore.resign.event.EmployeeRetiredEvent;
import com.peoplecore.resign.repository.ResignRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;

@Slf4j
@Component
public class LeaveAllowanceEventListener {
    // 사원 퇴직 완료 시 연차수당(RESIGNED) 자동 후보 + 산정
    // ResignService 트랜잭션 커밋 이후 수행

    private final LeaveAllowanceService leaveAllowanceService;
    private final EmployeeRepository employeeRepository;
    private final ResignRepository resignRepository;

    @Autowired
    public LeaveAllowanceEventListener(LeaveAllowanceService leaveAllowanceService,
                                       EmployeeRepository employeeRepository, ResignRepository resignRepository) {
        this.leaveAllowanceService = leaveAllowanceService;
        this.employeeRepository = employeeRepository;
        this.resignRepository = resignRepository;
    }

    @Order(1)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmployeeRetired(EmployeeRetiredEvent event) {
        try {
            // 1) Employee 조회
            Employee emp = employeeRepository.findById(event.getEmpId()).orElse(null);
            if (emp == null) {
                log.info("[LeaveAllowance] 사원 없음 - 자동 산정 스킵, empId={}", event.getEmpId());
                return;
            }

            // 2) 퇴직일 결정
            LocalDate resignDate = emp.getEmpResignDate();
            if (resignDate == null) {
                // CONFIRMED 시점 - Resign.resignDate fallback (SeveranceService 와 동일 패턴)
                resignDate = resignRepository
                        .findActiveOrConfirmedByEmpId(event.getCompanyId(), event.getEmpId())
                        .map(Resign::getResignDate).orElse(null);
            }
            if (resignDate == null) {
                log.info("[LeaveAllowance] 퇴직일 미설정 - 자동 산정 스킵, empId={}", event.getEmpId());
                return;
            }

            // 3) 자동 산정 호출
            leaveAllowanceService.createResignedAndCalculate(
                    event.getCompanyId(), event.getEmpId(), emp.getEmpResignDate());
            log.info("[LeaveAllowance] 퇴직자 연차수당 자동 산정 완료 - empId={}", event.getEmpId());
        } catch (Exception e) {
            log.error("[LeaveAllowance] 자동 산정 중 예외 - empId={}", event.getEmpId(), e);
        }
    }
}
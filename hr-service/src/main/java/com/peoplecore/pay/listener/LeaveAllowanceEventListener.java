package com.peoplecore.pay.listener;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.pay.service.LeaveAllowanceService;
import com.peoplecore.resign.event.EmployeeRetiredEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class LeaveAllowanceEventListener {
    // 사원 퇴직 완료 시 연차수당(RESIGNED) 자동 후보 + 산정
    // ResignService 트랜잭션 커밋 이후 수행

    private final LeaveAllowanceService leaveAllowanceService;
    private final EmployeeRepository employeeRepository;

    @Autowired
    public LeaveAllowanceEventListener(LeaveAllowanceService leaveAllowanceService,
                                       EmployeeRepository employeeRepository) {
        this.leaveAllowanceService = leaveAllowanceService;
        this.employeeRepository = employeeRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmployeeRetired(EmployeeRetiredEvent event) {
        try {
            // EmployeeRetiredEvent 에는 resignDate 가 없으므로 Employee 에서 조회
            Employee emp = employeeRepository.findById(event.getEmpId()).orElse(null);
            if (emp == null || emp.getEmpResignDate() == null) {
                log.info("[LeaveAllowance] 퇴직일 미설정 - 자동 산정 스킵, empId={}", event.getEmpId());
                return;
            }
            leaveAllowanceService.createResignedAndCalculate(
                    event.getCompanyId(), event.getEmpId(), emp.getEmpResignDate());
            log.info("[LeaveAllowance] 퇴직자 연차수당 자동 산정 완료 - empId={}", event.getEmpId());
        } catch (Exception e) {
            log.error("[LeaveAllowance] 자동 산정 중 예외 - empId={}", event.getEmpId(), e);
        }
    }
}
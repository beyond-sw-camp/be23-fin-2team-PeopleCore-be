package com.peoplecore.evaluation.listener;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.evaluation.service.EvaluatorRetirementHandler;
import com.peoplecore.resign.event.EmployeeRetiredEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 사원 퇴직 처리 이벤트(Spring ApplicationEvent) 리스너 — 평가자였으면 EvalGrade 정리 + 알림 발송.
// AFTER_COMMIT 으로 받아서 퇴직 트랜잭션 커밋된 후에만 동작 (롤백 시 false trigger 방지).
@Slf4j
@Component
public class EvaluatorRetirementListener {

    private final EmployeeRepository employeeRepository;
    private final EvaluatorRetirementHandler handler;

    public EvaluatorRetirementListener(EmployeeRepository employeeRepository,
                                       EvaluatorRetirementHandler handler) {
        this.employeeRepository = employeeRepository;
        this.handler = handler;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmployeeRetired(EmployeeRetiredEvent event) {
        // event 에 empId 만 있어서 사원 entity 다시 로드 — handler 가 회사/이름 정보 필요
        Employee retiredEmp = employeeRepository.findById(event.getEmpId()).orElse(null);
        if (retiredEmp == null) {
            log.warn("EmployeeRetiredEvent 수신했으나 사원 조회 실패 empId={}", event.getEmpId());
            return;
        }
        handler.handleEmployeeRetired(retiredEmp);
    }
}

package com.peoplecore.vacation.service;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.event.VacationApprovalDocCreatedEvent;
import com.peoplecore.event.VacationApprovalResultEvent;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.entity.VacationReq;
import com.peoplecore.vacation.repository.VacationReqRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
public class VacationReqService {

    private final VacationReqRepository vacationReqRepository;
    private final EmployeeRepository employeeRepository;

    @Autowired
    public VacationReqService(VacationReqRepository vacationReqRepository,
                              EmployeeRepository employeeRepository) {
        this.vacationReqRepository = vacationReqRepository;
        this.employeeRepository = employeeRepository;
    }

    /** Kafka(docCreated) Consumer 진입 — 결재문서 상신 성공 시점에 VacationReq insert (PENDING) */
    public void createFromApproval(VacationApprovalDocCreatedEvent event) {
        // 중복 insert 가드
        var existing = vacationReqRepository
                .findByCompanyIdAndApprovalDocId(event.getCompanyId(), event.getApprovalDocId());
        if (existing.isPresent()) {
            log.info("[VacationReq] docCreated 중복 수신 — 기존 vacReqId={}, docId={}",
                    existing.get().getVacReqId(), event.getApprovalDocId());
            return;
        }

        // 사원 조회
        Employee employee = employeeRepository.findById(event.getEmpId())
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        // VacationReq insert (PENDING). 스냅샷 값은 event 에서 그대로 받음
        VacationReq entity = VacationReq.builder()
                .companyId(event.getCompanyId())
                .infoId(event.getInfoId())
                .employee(employee)
                .reqEmpName(nz(event.getEmpName()))
                .reqEmpDeptName(nz(event.getDeptName()))
                .vacReqStartat(event.getVacReqStartat())
                .vacReqEndat(event.getVacReqEndat())
                .vacReqUseDay(event.getVacReqUseDay())
                .vacReqReason(event.getVacReqReason())
                .vacReqStatus(VacationStatus.PENDING)
                .reqEmpGrade(nz(event.getEmpGrade()))
                .reqEmpTitle(nz(event.getEmpTitle()))
                .approvalDocId(event.getApprovalDocId())
                .build();
        VacationReq saved = vacationReqRepository.save(entity);

        log.info("[VacationReq] docCreated → insert - vacReqId={}, docId={}, empId={}",
                saved.getVacReqId(), saved.getApprovalDocId(), employee.getEmpId());
    }

    /** Kafka(approvalResult) Consumer 진입 — 결재 결과 캐시 적용 */
    public void applyApprovalResult(VacationApprovalResultEvent event) {
        VacationReq req = vacationReqRepository
                .findByCompanyIdAndVacReqId(event.getCompanyId(), event.getVacReqId())
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_REQ_NOT_FOUND));

        VacationStatus newStatus = VacationStatus.valueOf(event.getStatus());

        Employee manager = (event.getManagerId() != null)
                ? employeeRepository.findById(event.getManagerId()).orElse(null)
                : null;

        req.applyApprovalResult(newStatus, manager, event.getRejectReason());

        log.info("[VacationReq] 결재 결과 반영 - vacReqId={}, status={}", req.getVacReqId(), newStatus);
    }

    /** null → 빈 문자열. NOT NULL 컬럼 안전 저장용 */
    private String nz(String s) {
        return s != null ? s : "";
    }
}

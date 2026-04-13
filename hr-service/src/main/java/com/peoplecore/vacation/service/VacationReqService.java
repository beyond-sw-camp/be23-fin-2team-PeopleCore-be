package com.peoplecore.vacation.service;

import com.peoplecore.department.domain.Department;
import com.peoplecore.department.repository.DepartmentRepository;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.event.VacationApprovalResultEvent;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.dto.VacationSubmitRequest;
import com.peoplecore.vacation.entity.VacationReq;
import com.peoplecore.vacation.entity.VacationStatus;
import com.peoplecore.vacation.repository.VacationReqRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@Transactional
public class VacationReqService {

    private final VacationReqRepository vacationReqRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;

    @Autowired
    public VacationReqService(VacationReqRepository vacationReqRepository,
                              EmployeeRepository employeeRepository,
                              DepartmentRepository departmentRepository) {
        this.vacationReqRepository = vacationReqRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
    }

    /** "확인" 클릭 → VacationReq insert (PENDING) → vacReqId 반환 */
    public Long submit(UUID companyId, Long empId, String empName, Long deptId,
                       String empGrade, String empTitle, VacationSubmitRequest req) {

        // 시간 정합성 가드
        if (!req.getVacReqEndat().isAfter(req.getVacReqStartat())) {
            throw new IllegalArgumentException(
                    "vacReqEndat 가 vacReqStartat 보다 같거나 이전 - start=" + req.getVacReqStartat()
                            + ", end=" + req.getVacReqEndat());
        }

        // 사원 조회
        Employee employee = employeeRepository.findById(empId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        // 부서 조회 (deptId → deptName 스냅샷, 회사 일치까지 검증)
        String deptName = "";
        if (deptId != null) {
            Department dept = departmentRepository
                    .findByDeptIdAndCompany_CompanyId(deptId, companyId)
                    .orElseThrow(() -> new CustomException(ErrorCode.DEPARTMENT_NOT_FOUND));
            deptName = dept.getDeptName();
        }

        // VacationReq 빌드 + insert (PENDING, docId=null)
        VacationReq entity = VacationReq.builder()
                .companyId(companyId)
                .infoId(req.getInfoId())
                .employee(employee)
                .reqEmpName(empName)
                .reqEmpDeptName(deptName)
                .vacReqStartat(req.getVacReqStartat())
                .vacReqEndat(req.getVacReqEndat())
                .vacReqUseDay(req.getVacReqUseDay())
                .vacReqReason(req.getVacReqReason())
                .vacReqStatus(VacationStatus.PENDING)
                .reqEmpGrade(empGrade != null ? empGrade : "")
                .reqEmpTitle(empTitle != null ? empTitle : "")
                .build();
        VacationReq saved = vacationReqRepository.save(entity);

        log.info("[VacationReq] 신청 생성 - vacReqId={}, empId={}", saved.getVacReqId(), empId);
        return saved.getVacReqId();
    }

    /** Kafka(docCreated) Consumer 진입 — approvalDocId bind */
    public void bindApprovalDoc(UUID companyId, Long vacReqId, Long docId) {
        VacationReq req = vacationReqRepository
                .findByCompanyIdAndVacReqId(companyId, vacReqId)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_REQ_NOT_FOUND));
        req.bindApprovalDoc(docId);
        log.info("[VacationReq] docId bind - vacReqId={}, docId={}", vacReqId, docId);
    }

    /** Kafka(approvalResult) Consumer 진입 — 결재 결과 캐시 적용 */
    public void applyApprovalResult(VacationApprovalResultEvent event) {

        // 회사 + vacReqId 라우팅 검증 조회
        VacationReq req = vacationReqRepository
                .findByCompanyIdAndVacReqId(event.getCompanyId(), event.getVacReqId())
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_REQ_NOT_FOUND));

        // status enum 변환
        VacationStatus newStatus = VacationStatus.valueOf(event.getStatus());

        // 최종 처리자 조회
        Employee manager = employeeRepository.findById(event.getManagerId())
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        // 도메인 메서드로 상태/처리자/반려사유 적용
        req.applyApprovalResult(newStatus, manager, event.getRejectReason());

        // docId 보완
        if (req.getApprovalDocId() == null && event.getApprovalDocId() != null) {
            req.bindApprovalDoc(event.getApprovalDocId());
        }
        log.info("[VacationReq] 결재 결과 반영 - vacReqId={}, status={}", req.getVacReqId(), newStatus);
    }
}

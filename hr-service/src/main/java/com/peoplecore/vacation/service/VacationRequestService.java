package com.peoplecore.vacation.service;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.event.VacationApprovalDocCreatedEvent;
import com.peoplecore.event.VacationApprovalResultEvent;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.dto.VacationRequestResponse;
import com.peoplecore.vacation.entity.RequestStatus;
import com.peoplecore.vacation.entity.VacationBalance;
import com.peoplecore.vacation.entity.VacationLedger;
import com.peoplecore.vacation.entity.VacationRequest;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationBalanceRepository;
import com.peoplecore.vacation.repository.VacationLedgerRepository;
import com.peoplecore.vacation.repository.VacationRequestQueryRepository;
import com.peoplecore.vacation.repository.VacationRequestRepository;
import com.peoplecore.vacation.repository.VacationTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/* 휴가 신청 서비스 - Kafka 진입 + 조회 + 취소 */
/* 취소 로직은 RequestStatus.cancelFrom (상태 패턴) 에 위임 */
@Service
@Slf4j
@Transactional
public class VacationRequestService {

    private final VacationRequestRepository vacationRequestRepository;
    private final VacationRequestQueryRepository vacationRequestQueryRepository;
    private final VacationTypeRepository vacationTypeRepository;
    private final EmployeeRepository employeeRepository;
    private final VacationBalanceRepository vacationBalanceRepository;
    private final VacationLedgerRepository vacationLedgerRepository;

    @Autowired
    public VacationRequestService(VacationRequestRepository vacationRequestRepository,
                                  VacationRequestQueryRepository vacationRequestQueryRepository,
                                  VacationTypeRepository vacationTypeRepository,
                                  EmployeeRepository employeeRepository,
                                  VacationBalanceRepository vacationBalanceRepository,
                                  VacationLedgerRepository vacationLedgerRepository) {
        this.vacationRequestRepository = vacationRequestRepository;
        this.vacationRequestQueryRepository = vacationRequestQueryRepository;
        this.vacationTypeRepository = vacationTypeRepository;
        this.employeeRepository = employeeRepository;
        this.vacationBalanceRepository = vacationBalanceRepository;
        this.vacationLedgerRepository = vacationLedgerRepository;
    }

    /* Kafka(vacation-approval-doc-created) 진입 - PENDING INSERT + markPending */
    public void createFromApproval(VacationApprovalDocCreatedEvent event) {
        Optional<VacationRequest> existing = vacationRequestRepository
                .findByCompanyIdAndApprovalDocId(event.getCompanyId(), event.getApprovalDocId());
        if (existing.isPresent()) {
            log.info("[VacationRequest] docCreated 중복 수신 - 기존 requestId={}, docId={}",
                    existing.get().getRequestId(), event.getApprovalDocId());
            return;
        }

        Employee employee = employeeRepository.findById(event.getEmpId())
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
        VacationType vacationType = vacationTypeRepository.findById(event.getInfoId())
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_TYPE_NOT_FOUND));

        Integer balanceYear = event.getVacReqStartat().toLocalDate().getYear();
        VacationBalance balance = vacationBalanceRepository
                .findOne(event.getCompanyId(), employee.getEmpId(), vacationType.getTypeId(), balanceYear)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_BALANCE_INSUFFICIENT));

        balance.markPending(event.getVacReqUseDay());

        VacationRequest.EmployeeSnapshot snapshot = new VacationRequest.EmployeeSnapshot(
                event.getEmpName(),
                event.getDeptName(),
                event.getEmpGrade(),
                event.getEmpTitle()
        );

        VacationRequest request = VacationRequest.createPending(
                event.getCompanyId(),
                vacationType,
                employee,
                snapshot,
                event.getVacReqStartat(),
                event.getVacReqEndat(),
                event.getVacReqUseDay(),
                event.getVacReqReason(),
                event.getApprovalDocId()
        );
        VacationRequest saved = vacationRequestRepository.save(request);

        log.info("[VacationRequest] docCreated → INSERT + markPending - requestId={}, docId={}, empId={}, typeId={}, useDays={}",
                saved.getRequestId(), saved.getApprovalDocId(), employee.getEmpId(),
                vacationType.getTypeId(), event.getVacReqUseDay());
    }

    /* Kafka(vacation-approval-result) 진입 - 상태 전이 + 상태 패턴 위임 */
    public void applyApprovalResult(VacationApprovalResultEvent event) {
        VacationRequest request = vacationRequestRepository
                .findByCompanyIdAndRequestId(event.getCompanyId(), event.getVacReqId())
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_REQ_NOT_FOUND));

        RequestStatus newStatus = RequestStatus.valueOf(event.getStatus());

        Employee manager = (event.getManagerId() != null)
                ? employeeRepository.findById(event.getManagerId())
                    .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND))
                : null;

        request.apply(newStatus, manager, event.getRejectReason());

        Integer balanceYear = request.getRequestStartAt().toLocalDate().getYear();
        VacationBalance balance = vacationBalanceRepository
                .findOne(request.getCompanyId(),
                         request.getEmployee().getEmpId(),
                         request.getVacationType().getTypeId(),
                         balanceYear)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_BALANCE_INSUFFICIENT));

        Optional<VacationLedger> ledgerToSave = newStatus.applyKafkaResult(
                balance,
                request.getRequestUseDays(),
                request.getRequestId(),
                event.getManagerId()
        );
        ledgerToSave.ifPresent(vacationLedgerRepository::save);

        log.info("[VacationRequest] 결재 결과 반영 - requestId={}, status={}, managerId={}",
                request.getRequestId(), newStatus, event.getManagerId());
    }

    /* 내 신청 이력 페이지 조회 - Type fetch join */
    @Transactional(readOnly = true)
    public Page<VacationRequestResponse> listMine(UUID companyId, Long empId, Pageable pageable) {
        return vacationRequestQueryRepository
                .findEmployeeHistory(companyId, empId, pageable)
                .map(VacationRequestResponse::from);
    }

    /* 관리자 상태별 조회 페이지 - Type + Employee fetch join */
    @Transactional(readOnly = true)
    public Page<VacationRequestResponse> listForAdmin(UUID companyId, RequestStatus status, Pageable pageable) {
        return vacationRequestQueryRepository
                .findByCompanyAndStatus(companyId, status, pageable)
                .map(VacationRequestResponse::from);
    }

    /* 사원 셀프 취소 - 본인 request 검증 + 정상 전이 규칙 적용 */
    /* PENDING→CANCELED / APPROVED→CANCELED 만 허용. 그 외는 RequestStatus.apply 에서 INVALID_REQUEST_STATUS_TRANSITION */
    public void cancelByEmployee(UUID companyId, Long empId, Long requestId, String reason) {
        VacationRequest request = loadRequestForCompany(companyId, requestId);
        if (!request.getEmployee().getEmpId().equals(empId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        cancelInternal(request, empId, reason, false);
    }

    /* 관리자 직권 취소 - 규칙 우회 (applyByAdmin). 그래도 cancelFrom 은 PENDING/APPROVED 에서만 동작 */
    /* REJECTED/CANCELED 에서 호출되면 cancelFrom 에서 UnsupportedOperationException */
    public void cancelByAdmin(UUID companyId, Long managerId, Long requestId, String reason) {
        VacationRequest request = loadRequestForCompany(companyId, requestId);
        cancelInternal(request, managerId, reason, true);
    }

    /* 공통 취소 로직 - currentStatus 캡처 → 상태 변경 → cancelFrom 위임 */
    private void cancelInternal(VacationRequest request, Long actorId, String reason, boolean isAdmin) {
        /* 현재 상태 캡처 - apply 호출 후엔 CANCELED 가 되어 cancelFrom 이 throw 함 */
        RequestStatus currentStatus = request.getRequestStatus();

        Employee actor = employeeRepository.findById(actorId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        if (isAdmin) {
            request.applyByAdmin(RequestStatus.CANCELED, actor, reason);
        } else {
            request.apply(RequestStatus.CANCELED, actor, reason);
        }

        Integer balanceYear = request.getRequestStartAt().toLocalDate().getYear();
        VacationBalance balance = vacationBalanceRepository
                .findOne(request.getCompanyId(),
                         request.getEmployee().getEmpId(),
                         request.getVacationType().getTypeId(),
                         balanceYear)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_BALANCE_INSUFFICIENT));

        /* 상태 패턴 - currentStatus(PENDING→releasePending / APPROVED→restore+RESTORED ledger) */
        Optional<VacationLedger> ledgerToSave = currentStatus.cancelFrom(
                balance,
                request.getRequestUseDays(),
                request.getRequestId(),
                actorId,
                reason);
        ledgerToSave.ifPresent(vacationLedgerRepository::save);

        log.info("[VacationRequest] 취소 완료 - requestId={}, from={}, actorId={}, isAdmin={}",
                request.getRequestId(), currentStatus, actorId, isAdmin);
    }

    /* 회사 + requestId 단건 조회 - 타 회사 차단 */
    private VacationRequest loadRequestForCompany(UUID companyId, Long requestId) {
        return vacationRequestRepository.findByCompanyIdAndRequestId(companyId, requestId)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_REQ_NOT_FOUND));
    }
}
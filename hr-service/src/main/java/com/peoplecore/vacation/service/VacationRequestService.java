package com.peoplecore.vacation.service;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.event.VacationApprovalDocCreatedEvent;
import com.peoplecore.event.VacationApprovalResultEvent;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.dto.VacationAdminPeriodResponseDto;
import com.peoplecore.vacation.dto.VacationRequestResponse;
import com.peoplecore.vacation.entity.AbstractApprovalBoundRequest;
import com.peoplecore.vacation.entity.RequestStatus;
import com.peoplecore.vacation.entity.VacationBalance;
import com.peoplecore.vacation.entity.VacationLedger;
import com.peoplecore.vacation.entity.VacationPolicy;
import com.peoplecore.vacation.entity.VacationRequest;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationBalanceRepository;
import com.peoplecore.vacation.repository.VacationLedgerRepository;
import com.peoplecore.vacation.repository.VacationPolicyRepository;
import com.peoplecore.vacation.repository.VacationRequestQueryRepository;
import com.peoplecore.vacation.repository.VacationRequestRepository;
import com.peoplecore.vacation.repository.VacationTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/* 휴가 사용 신청 서비스 - Kafka 진입 + 조회 + 취소 */
/* 보유 잔여(VacationBalance) 에서 markPending → consume 플로우 (RequestStatus 상태 패턴 위임) */
/* 법정 부여 신청(GRANT) 은 VacationGrantRequestService 에서 별도 처리 */
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
    private final VacationPolicyRepository vacationPolicyRepository;

    @Autowired
    public VacationRequestService(VacationRequestRepository vacationRequestRepository,
                                  VacationRequestQueryRepository vacationRequestQueryRepository,
                                  VacationTypeRepository vacationTypeRepository,
                                  EmployeeRepository employeeRepository,
                                  VacationBalanceRepository vacationBalanceRepository,
                                  VacationLedgerRepository vacationLedgerRepository,
                                  VacationPolicyRepository vacationPolicyRepository) {
        this.vacationRequestRepository = vacationRequestRepository;
        this.vacationRequestQueryRepository = vacationRequestQueryRepository;
        this.vacationTypeRepository = vacationTypeRepository;
        this.employeeRepository = employeeRepository;
        this.vacationBalanceRepository = vacationBalanceRepository;
        this.vacationLedgerRepository = vacationLedgerRepository;
        this.vacationPolicyRepository = vacationPolicyRepository;
    }

    /* Kafka(vacation-approval-doc-created) 진입 - PENDING INSERT + Balance markPending */
    public void createFromApproval(VacationApprovalDocCreatedEvent event) {
        // 중복 수신 방어 - 같은 결재 문서 재처리 시 no-op
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

        // 성별 제한 검증
        if (!vacationType.getGenderLimit().allows(employee.getEmpGender())) {
            throw new CustomException(ErrorCode.VACATION_TYPE_GENDER_NOT_ALLOWED);
        }

        // 보유 Balance 에서 markPending. allowAdvanceUse 회사면 available 검증 스킵
        Integer balanceYear = event.getVacReqStartat().toLocalDate().getYear();
        VacationBalance balance = vacationBalanceRepository
                .findOne(event.getCompanyId(), employee.getEmpId(), vacationType.getTypeId(), balanceYear)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_BALANCE_INSUFFICIENT));
        boolean allowNegative = isAdvanceUseAllowed(event.getCompanyId(), vacationType);
        balance.markPending(event.getVacReqUseDay(), allowNegative);

        AbstractApprovalBoundRequest.EmployeeSnapshot snapshot = new AbstractApprovalBoundRequest.EmployeeSnapshot(
                event.getEmpName(), event.getDeptName(), event.getEmpGrade(), event.getEmpTitle());

        VacationRequest request = VacationRequest.createPending(
                event.getCompanyId(), vacationType, employee, snapshot,
                event.getVacReqStartat(), event.getVacReqEndat(),
                event.getVacReqUseDay(), event.getVacReqReason(), event.getApprovalDocId());
        VacationRequest saved = vacationRequestRepository.save(request);

        log.info("[VacationRequest] docCreated → INSERT - requestId={}, docId={}, empId={}, typeId={}, useDays={}",
                saved.getRequestId(), saved.getApprovalDocId(), employee.getEmpId(),
                vacationType.getTypeId(), event.getVacReqUseDay());
    }

    /* Kafka(vacation-approval-result) 진입 - 상태 전이 + Balance 반영 */
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

        // 상태 패턴 위임: pending→used or releasePending
        Integer balanceYear = request.getRequestStartAt().toLocalDate().getYear();
        VacationBalance balance = vacationBalanceRepository
                .findOne(request.getCompanyId(),
                         request.getEmployee().getEmpId(),
                         request.getVacationType().getTypeId(),
                         balanceYear)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_BALANCE_INSUFFICIENT));

        Optional<VacationLedger> ledgerToSave = newStatus.applyKafkaResult(
                balance, request.getRequestUseDays(), request.getRequestId(), event.getManagerId());
        ledgerToSave.ifPresent(vacationLedgerRepository::save);

        log.info("[VacationRequest] 결재 결과 반영 - requestId={}, status={}, managerId={}",
                request.getRequestId(), newStatus, event.getManagerId());
    }

    /* 전사 휴가 관리 - 기간 교집합 + 상태 복수 필터 페이지 */
    /* statuses null or 빈 배열 이면 상태 필터 없이 전체. 경계 포함: startDate 00:00 ~ endDate 23:59:59 */
    @Transactional(readOnly = true)
    public Page<VacationAdminPeriodResponseDto> listForAdminByPeriod(UUID companyId,
                                                                  LocalDate startDate,
                                                                  LocalDate endDate,
                                                                  List<RequestStatus> statuses,
                                                                  Pageable pageable) {
        LocalDateTime periodStart = startDate.atStartOfDay();
        LocalDateTime periodEnd = endDate.atTime(23, 59, 59);

        return vacationRequestQueryRepository
                .findByCompanyAndPeriodAndStatuses(companyId, periodStart, periodEnd, statuses, pageable)
                .map(VacationAdminPeriodResponseDto::from);
    }

    /* 관리자 상태별 조회 페이지 - Type + Employee fetch join */
    @Transactional(readOnly = true)
    public Page<VacationRequestResponse> listForAdmin(UUID companyId, RequestStatus status, Pageable pageable) {
        return vacationRequestQueryRepository
                .findByCompanyAndStatus(companyId, status, pageable)
                .map(VacationRequestResponse::from);
    }

    /* 관리자 직권 취소 - 규칙 우회 (applyByAdmin) */
    public void cancelByAdmin(UUID companyId, Long managerId, Long requestId, String reason) {
        VacationRequest request = loadRequestForCompany(companyId, requestId);
        cancelInternal(request, managerId, reason, true);
    }

    /* 공통 취소 로직 - 상태 패턴 cancelFrom 위임 (releasePending or restore) */
    private void cancelInternal(VacationRequest request, Long actorId, String reason, boolean isAdmin) {
        RequestStatus currentStatus = request.getRequestStatus();   // apply 전 캡처

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

        Optional<VacationLedger> ledgerToSave = currentStatus.cancelFrom(
                balance, request.getRequestUseDays(), request.getRequestId(), actorId, reason);
        ledgerToSave.ifPresent(vacationLedgerRepository::save);

        log.info("[VacationRequest] 취소 완료 - requestId={}, from={}, actorId={}, isAdmin={}",
                request.getRequestId(), currentStatus, actorId, isAdmin);
    }

    /* 회사 + requestId 단건 조회 - 타 회사 차단 */
    private VacationRequest loadRequestForCompany(UUID companyId, Long requestId) {
        return vacationRequestRepository.findByCompanyIdAndRequestId(companyId, requestId)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_REQ_NOT_FOUND));
    }

    /* allowAdvanceUse 정책 + 연차/월차 유형 동시 만족 시 true (available 검증 스킵 대상) */
    /* 그 외 (법정휴가 / 정책 OFF / 정책 없음) 는 false - 기존 엄격 검증 유지 */
    private boolean isAdvanceUseAllowed(UUID companyId, VacationType vacationType) {
        if (!vacationType.isAnnual() && !vacationType.isMonthly()) return false;
        return vacationPolicyRepository.findByCompanyId(companyId)
                .map(VacationPolicy::isAdvanceUseActive)
                .orElse(false);
    }
}

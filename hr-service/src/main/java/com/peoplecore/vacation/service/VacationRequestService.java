package com.peoplecore.vacation.service;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.event.VacationApprovalDocCreatedEvent;
import com.peoplecore.event.VacationApprovalResultEvent;
import com.peoplecore.event.VacationSlotItem;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.dto.MyVacationTypeResponseDto;
import com.peoplecore.vacation.dto.VacationAdminPeriodResponseDto;
import com.peoplecore.vacation.dto.VacationRequestResponse;
import com.peoplecore.vacation.entity.AbstractApprovalBoundRequest;
import com.peoplecore.vacation.entity.RequestStatus;
import com.peoplecore.vacation.entity.VacationBalance;
import com.peoplecore.vacation.entity.VacationLedger;
import com.peoplecore.vacation.entity.VacationPolicy;
import com.peoplecore.vacation.entity.VacationRequest;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationBalanceQueryRepository;
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

import java.math.BigDecimal;
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
    private final VacationBalanceQueryRepository vacationBalanceQueryRepository;

    @Autowired
    public VacationRequestService(VacationRequestRepository vacationRequestRepository,
                                  VacationRequestQueryRepository vacationRequestQueryRepository,
                                  VacationTypeRepository vacationTypeRepository,
                                  EmployeeRepository employeeRepository,
                                  VacationBalanceRepository vacationBalanceRepository,
                                  VacationLedgerRepository vacationLedgerRepository,
                                  VacationPolicyRepository vacationPolicyRepository,
                                  VacationBalanceQueryRepository vacationBalanceQueryRepository) {
        this.vacationRequestRepository = vacationRequestRepository;
        this.vacationRequestQueryRepository = vacationRequestQueryRepository;
        this.vacationTypeRepository = vacationTypeRepository;
        this.employeeRepository = employeeRepository;
        this.vacationBalanceRepository = vacationBalanceRepository;
        this.vacationLedgerRepository = vacationLedgerRepository;
        this.vacationPolicyRepository = vacationPolicyRepository;
        this.vacationBalanceQueryRepository = vacationBalanceQueryRepository;
    }

    /* Kafka(vacation-approval-doc-created) 진입 - PENDING row N건 INSERT + Balance markPending 합계 */
    public void createFromApproval(VacationApprovalDocCreatedEvent event) {
        // 중복 수신 방어 - 같은 approvalDocId 그룹이 이미 존재하면 no-op
        List<VacationRequest> existing = vacationRequestRepository
                .findByCompanyIdAndApprovalDocId(event.getCompanyId(), event.getApprovalDocId());
        if (!existing.isEmpty()) {
            log.info("[VacationRequest] docCreated 중복 수신 skip - docId={}, groupSize={}",
                    event.getApprovalDocId(), existing.size());
            return;
        }

        // items 필수 - 비어있으면 즉시 실패 (Consumer @RetryableTopic excludes 에 CustomException 포함)
        List<VacationSlotItem> items = event.getItems();
        if (items == null || items.isEmpty()) {
            throw new CustomException(ErrorCode.VACATION_REQ_ITEMS_EMPTY);
        }

        Employee employee = employeeRepository.findById(event.getEmpId())
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
        VacationType vacationType = vacationTypeRepository.findById(event.getInfoId())
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_TYPE_NOT_FOUND));

        // 성별 제한 검증 - 임신/출산 등 성별 한정 유형 조기 차단
        if (!vacationType.getGenderLimit().allows(employee.getEmpGender())) {
            throw new CustomException(ErrorCode.VACATION_TYPE_GENDER_NOT_ALLOWED);
        }

        // 그룹 합계 - Balance 차감/Ledger 기록 단위
        BigDecimal totalDays = items.stream()
                .map(VacationSlotItem::getUseDay)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Balance 조회 - 첫 슬롯 기준 연도. 없으면 미리쓰기 정책에 따라 자동 생성 or 엄격 차단
        Integer balanceYear = items.get(0).getStartAt().toLocalDate().getYear();
        boolean allowNegative = isAdvanceUseAllowed(event.getCompanyId(), vacationType);
        VacationBalance balance = vacationBalanceRepository
                .findOne(event.getCompanyId(), employee.getEmpId(), vacationType.getTypeId(), balanceYear)
                .orElseGet(() -> {
                    // 미리쓰기 비허용(법정휴가 / 정책 OFF) → 기존 엄격 모드 유지
                    if (!allowNegative) {
                        throw new CustomException(ErrorCode.VACATION_BALANCE_INSUFFICIENT);
                    }
                    LocalDate today = LocalDate.now();
                    // 월차: hireDate+1년 / 연차: today+1년-1일 (스케줄러 규칙과 동일)
                    LocalDate expiresAt = vacationType.isMonthly()
                            ? employee.getEmpHireDate().plusYears(1)
                            : today.plusYears(1).minusDays(1);
                    log.info("[VacationRequest] Balance 자동 생성(미리쓰기) - empId={}, typeId={}, year={}, expiresAt={}",
                            employee.getEmpId(), vacationType.getTypeId(), balanceYear, expiresAt);
                    return vacationBalanceRepository.save(
                            VacationBalance.createNew(
                                    event.getCompanyId(), vacationType, employee,
                                    balanceYear, today, expiresAt));
                });

        // 합계로 한 번만 markPending - 그룹 불변식(모든 슬롯 동일 상태) 유지
        balance.markPending(totalDays, allowNegative);

        AbstractApprovalBoundRequest.EmployeeSnapshot snapshot = new AbstractApprovalBoundRequest.EmployeeSnapshot(
                event.getEmpName(), event.getDeptName(), event.getEmpGrade(), event.getEmpTitle());

        // 슬롯별 row insert - 같은 approvalDocId 로 그룹핑 (findByCompanyIdAndApprovalDocId 그룹키)
        items.forEach(item -> {
            VacationRequest request = VacationRequest.createPending(
                    event.getCompanyId(), vacationType, employee, snapshot,
                    item.getStartAt(), item.getEndAt(),
                    item.getUseDay(), event.getVacReqReason(), event.getApprovalDocId());
            vacationRequestRepository.save(request);
        });

        log.info("[VacationRequest] docCreated → INSERT - docId={}, empId={}, typeId={}, slotCount={}, totalDays={}",
                event.getApprovalDocId(), employee.getEmpId(),
                vacationType.getTypeId(), items.size(), totalDays);
    }

    /* Kafka(vacation-approval-result) 진입 - 그룹 전체 상태 전이 + Balance 그룹 합계 반영 */
    /* 멱등: 이미 newStatus 로 전이된 그룹은 no-op (첫 처리 결과 보존) */
    /* CANCELED 는 기안자 회수 경로. PENDING 에서만 수신 가정 (APPROVED→CANCELED 는 관리자 직권 API) */
    public void applyApprovalResult(VacationApprovalResultEvent event) {
        List<VacationRequest> group = vacationRequestRepository
                .findByCompanyIdAndApprovalDocId(event.getCompanyId(), event.getApprovalDocId());
        if (group.isEmpty()) {
            throw new CustomException(ErrorCode.VACATION_REQ_NOT_FOUND);
        }

        RequestStatus newStatus = RequestStatus.valueOf(event.getStatus());
        RequestStatus currentStatus = group.get(0).getRequestStatus();   // 그룹 불변식: 전원 동일 상태

        // 멱등 가드: 이미 같은 상태면 첫 처리 결과 보존하고 no-op
        if (currentStatus == newStatus) {
            log.info("[VacationRequest] applyApprovalResult 중복 수신 skip - docId={}, status={}",
                    event.getApprovalDocId(), newStatus);
            return;
        }

        Employee manager = (event.getManagerId() != null)
                ? employeeRepository.findById(event.getManagerId())
                    .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND))
                : null;

        // Balance 조회 - 그룹 대표(첫 row) 기준. 모든 row 가 같은 empId/typeId/year 공유
        VacationRequest first = group.get(0);
        Integer balanceYear = first.getRequestStartAt().toLocalDate().getYear();
        VacationBalance balance = vacationBalanceRepository
                .findOne(first.getCompanyId(),
                         first.getEmployee().getEmpId(),
                         first.getVacationType().getTypeId(),
                         balanceYear)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_BALANCE_NOT_FOUND));

        // 그룹 합계 - Balance/Ledger 반영 단위
        BigDecimal totalDays = group.stream()
                .map(VacationRequest::getRequestUseDays)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 상태 패턴: 합계로 한 번만 transition. Ledger 참조는 그룹 대표 requestId
        Optional<VacationLedger> ledgerToSave = currentStatus.kafkaTransitionTo(
                newStatus, balance, totalDays,
                first.getRequestId(), event.getManagerId(), event.getRejectReason());

        // 모든 슬롯 row 일괄 전이 (그룹 불변식 유지)
        group.forEach(r -> r.apply(newStatus, manager, event.getRejectReason()));
        ledgerToSave.ifPresent(vacationLedgerRepository::save);

        log.info("[VacationRequest] 결재 결과 반영 - docId={}, slots={}, {}→{}, managerId={}",
                event.getApprovalDocId(), group.size(), currentStatus, newStatus, event.getManagerId());
    }

    /* 전사 휴가 관리 - 기간 교집합 + 상태 필터 / 사원별 요약 페이지 */
    /* 페이지 단위 = 사원(중복 제거). totalElements = 기간 내 휴가자 수 */
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
                .findByCompanyAndPeriodAndStatuses(companyId, periodStart, periodEnd, statuses, pageable);
    }

    /* 관리자 상태별 조회 페이지 - Type + Employee fetch join */
    @Transactional(readOnly = true)
    public Page<VacationRequestResponse> listForAdmin(UUID companyId, RequestStatus status, Pageable pageable) {
        return vacationRequestQueryRepository
                .findByCompanyAndStatus(companyId, status, pageable)
                .map(VacationRequestResponse::from);
    }

    /* 본인 휴가 신청 이력 페이지 (createdAt DESC) - Type fetch join, 응답 DTO 매핑 */
    /* 화면 "내 신청 이력" 탭 - 상태(PENDING/APPROVED/REJECTED/CANCELED) 전부 포함 */
    @Transactional(readOnly = true)
    public Page<VacationRequestResponse> listMine(UUID companyId, Long empId, Pageable pageable) {
        return vacationRequestQueryRepository.findEmployeeHistory(companyId, empId, pageable)
                .map(VacationRequestResponse::from);
    }

    /* 관리자 직권 취소 - 규칙 우회 (applyByAdmin) */
    public void cancelByAdmin(UUID companyId, Long managerId, Long requestId, String reason) {
        VacationRequest request = loadRequestForCompany(companyId, requestId);
        cancelInternal(request, managerId, reason, true);
    }

    /* 공통 취소 로직 - 그룹 일괄 취소. 상태 패턴 cancelFrom 위임 (releasePending or restore) */
    private void cancelInternal(VacationRequest request, Long actorId, String reason, boolean isAdmin) {
        // 그룹 전체 로드 - 불변식: 같은 approvalDocId 의 모든 slot 은 같은 상태
        List<VacationRequest> group = vacationRequestRepository
                .findByCompanyIdAndApprovalDocId(request.getCompanyId(), request.getApprovalDocId());
        if (group.isEmpty()) {
            throw new CustomException(ErrorCode.VACATION_REQ_NOT_FOUND);
        }

        RequestStatus currentStatus = group.get(0).getRequestStatus();   // apply 전 캡처

        // 회사 스코프 내 actor 조회 - 타 회사 empId 탈취 방어 (Gateway 보장 이중 방어)
        Employee actor = employeeRepository.findByEmpIdAndCompany_CompanyId(actorId, request.getCompanyId())
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        // 모든 슬롯 일괄 전이
        group.forEach(r -> {
            if (isAdmin) {
                r.applyByAdmin(RequestStatus.CANCELED, actor, reason);
            } else {
                r.apply(RequestStatus.CANCELED, actor, reason);
            }
        });

        // Balance 조회 - 그룹 대표 기준
        VacationRequest first = group.get(0);
        Integer balanceYear = first.getRequestStartAt().toLocalDate().getYear();
        VacationBalance balance = vacationBalanceRepository
                .findOne(first.getCompanyId(),
                         first.getEmployee().getEmpId(),
                         first.getVacationType().getTypeId(),
                         balanceYear)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_BALANCE_NOT_FOUND));

        // 그룹 합계로 한 번만 cancelFrom (markPending 이 합계였으므로 대칭)
        BigDecimal totalDays = group.stream()
                .map(VacationRequest::getRequestUseDays)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Optional<VacationLedger> ledgerToSave = currentStatus.cancelFrom(
                balance, totalDays, first.getRequestId(), actorId, reason);
        ledgerToSave.ifPresent(vacationLedgerRepository::save);

        log.info("[VacationRequest] 취소 완료 - docId={}, slots={}, from={}, actorId={}, isAdmin={}",
                first.getApprovalDocId(), group.size(), currentStatus, actorId, isAdmin);
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

    /* 본인 현시점 유효 Balance + 드롭다운 누락 보강 - 휴가 사용 신청 모달 드롭다운 */
    /* 보유 Balance (expires_at 유효 + isActive=true) 는 기존대로 반환 */
    /* 입사 1년 경과 → 연차, 미만 → 월차 중 누락된 유형 1개를 remaining=0 으로 추가 노출 */
    /* (적립 스케줄러 실행 전·신입 4일차처럼 Balance row 가 아직 없는 케이스 커버) */
    /* allowAdvance: 회사정책 ON + 연차/월차일 때 true - 프론트 사전 검증용 */
    /* 실제 음수 차감 차단은 submit 시점(createFromApproval) 에서 수행 */
    @Transactional(readOnly = true)
    public List<MyVacationTypeResponseDto> listMyVacationTypes(UUID companyId, Long empId) {
        LocalDate today = LocalDate.now();
        /* 회사 정책 한 번만 조회 - 유형마다 반복 조회 방지 */
        boolean companyAdvanceActive = vacationPolicyRepository.findByCompanyId(companyId)
                .map(VacationPolicy::isAdvanceUseActive)
                .orElse(false);

        /* 기존 보유 Balance 기반 목록 (정렬: VacationType.sortOrder ASC) */
        List<VacationBalance> balances = vacationBalanceQueryRepository
                .findActiveByEmpFetchType(companyId, empId, today);

        List<MyVacationTypeResponseDto> result = new java.util.ArrayList<>();
        balances.forEach(b -> result.add(MyVacationTypeResponseDto.from(
                b,
                companyAdvanceActive && (b.getVacationType().isAnnual() || b.getVacationType().isMonthly()))));

        /* 드롭다운 보강 - 입사 1년 경과 여부에 따라 연차/월차 중 누락된 유형 1개만 추가 */
        Employee employee = employeeRepository.findById(empId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
        boolean overOneYear = !employee.getEmpHireDate().plusYears(1).isAfter(today);
        String targetCode = overOneYear ? VacationType.CODE_ANNUAL : VacationType.CODE_MONTHLY;

        boolean alreadyPresent = balances.stream()
                .anyMatch(b -> targetCode.equals(b.getVacationType().getTypeCode()));
        if (!alreadyPresent) {
            /* 유형이 회사에 등록/활성화돼 있을 때만 보강. 없으면 skip */
            vacationTypeRepository.findByCompanyIdAndTypeCode(companyId, targetCode)
                    .filter(t -> Boolean.TRUE.equals(t.getIsActive()))
                    .ifPresent(type -> result.add(
                            MyVacationTypeResponseDto.ofEmpty(type, today.getYear(), companyAdvanceActive)));
        }

        /* 보강분까지 포함해 sortOrder ASC 재정렬 */
        result.sort(java.util.Comparator.comparing(
                MyVacationTypeResponseDto::getSortOrder,
                java.util.Comparator.nullsLast(Integer::compareTo)));
        return result;
    }
}

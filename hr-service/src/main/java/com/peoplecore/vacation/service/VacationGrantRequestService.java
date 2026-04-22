package com.peoplecore.vacation.service;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.event.VacationApprovalResultEvent;
import com.peoplecore.event.VacationGrantApprovalDocCreatedEvent;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.dto.VacationGrantRequestResponse;
import com.peoplecore.vacation.entity.AbstractApprovalBoundRequest;
import com.peoplecore.vacation.entity.RequestStatus;
import com.peoplecore.vacation.entity.StatutoryVacationType;
import com.peoplecore.vacation.entity.VacationBalance;
import com.peoplecore.vacation.entity.VacationGrantRequest;
import com.peoplecore.vacation.entity.VacationLedger;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationBalanceRepository;
import com.peoplecore.vacation.repository.VacationGrantRequestRepository;
import com.peoplecore.vacation.repository.VacationLedgerRepository;
import com.peoplecore.vacation.repository.VacationTypeRepository;
import com.peoplecore.vacation.util.MiscarriageLeaveRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/* 휴가 부여 신청 서비스 - Kafka 진입 + 조회 + 취소 */
/* 사원이 "이 휴가를 달라" 고 증빙 첨부해 결재 → 승인 시 Balance.accrue (사용은 별도 USE 신청) */
/* 다층 동시성 방어: Redis 분산 락(신청 단계) + @Version 낙관적 락(승인 단계) + cap 자동 REJECT(최종 가드) */
@Service
@Slf4j
@Transactional
public class VacationGrantRequestService {

    /* 분산 락 키 prefix - (회사,사원,유형) 단위로 동시 신청 race 차단 */
    private static final String LOCK_KEY_PREFIX = "vacation:grant:lock";
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);

    private final VacationGrantRequestRepository vacationGrantRequestRepository;
    private final VacationTypeRepository vacationTypeRepository;
    private final EmployeeRepository employeeRepository;
    private final VacationBalanceRepository vacationBalanceRepository;
    private final VacationLedgerRepository vacationLedgerRepository;
    private final StringRedisTemplate stringRedisTemplate;

    @Autowired
    public VacationGrantRequestService(VacationGrantRequestRepository vacationGrantRequestRepository,
                                       VacationTypeRepository vacationTypeRepository,
                                       EmployeeRepository employeeRepository,
                                       VacationBalanceRepository vacationBalanceRepository,
                                       VacationLedgerRepository vacationLedgerRepository,
                                       StringRedisTemplate stringRedisTemplate) {
        this.vacationGrantRequestRepository = vacationGrantRequestRepository;
        this.vacationTypeRepository = vacationTypeRepository;
        this.employeeRepository = employeeRepository;
        this.vacationBalanceRepository = vacationBalanceRepository;
        this.vacationLedgerRepository = vacationLedgerRepository;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /* Kafka(vacation-grant-approval-doc-created) 진입 - PENDING INSERT (Balance 무변경 + cap 검증) */
    /* 1층 방어: Redis 분산 락으로 같은 (회사,사원,유형) 동시 신청 race 원천 차단 */
    public void createFromApproval(VacationGrantApprovalDocCreatedEvent event) {
        String lockKey = LOCK_KEY_PREFIX + ":" + event.getCompanyId()
                + ":" + event.getEmpId() + ":" + event.getInfoId();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            // 같은 사원/유형 신청이 동시 처리 중 - Kafka retry 로 재시도 유도
            throw new CustomException(ErrorCode.CONCURRENT_REQUEST_LOCK_FAILED);
        }
        try {
            createFromApprovalLocked(event);
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    /* 락 보유 상태에서 실제 신청 처리 - validateCap → INSERT */
    private void createFromApprovalLocked(VacationGrantApprovalDocCreatedEvent event) {
        // 중복 수신 방어
        Optional<VacationGrantRequest> existing = vacationGrantRequestRepository
                .findByCompanyIdAndApprovalDocId(event.getCompanyId(), event.getApprovalDocId());
        if (existing.isPresent()) {
            log.info("[VacationGrantRequest] grantDocCreated 중복 수신 - 기존 requestId={}, docId={}",
                    existing.get().getRequestId(), event.getApprovalDocId());
            return;
        }

        Employee employee = employeeRepository.findById(event.getEmpId())
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
        VacationType vacationType = vacationTypeRepository.findById(event.getInfoId())
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_TYPE_NOT_FOUND));

        // 성별 제한
        if (!vacationType.getGenderLimit().allows(employee.getEmpGender())) {
            throw new CustomException(ErrorCode.VACATION_TYPE_GENDER_NOT_ALLOWED);
        }

        StatutoryVacationType statutoryType = StatutoryVacationType.fromCode(vacationType.getTypeCode());

        // MISCARRIAGE 면 주수 → 일수 자동 산정 (입력 useDays 무시하고 계산값 사용)
        BigDecimal useDays = event.getVacReqUseDay();
        if (statutoryType == StatutoryVacationType.MISCARRIAGE) {
            if (event.getPregnancyWeeks() == null) {
                throw new CustomException(ErrorCode.VACATION_REQ_PREGNANCY_WEEKS_REQUIRED);
            }
            useDays = MiscarriageLeaveRule.daysForWeeks(event.getPregnancyWeeks());
        }

        // cap 검증 - 신청 시점(현재) 연도 기준
        validateCap(event.getCompanyId(), employee.getEmpId(), vacationType, statutoryType, useDays);

        AbstractApprovalBoundRequest.EmployeeSnapshot snapshot = new AbstractApprovalBoundRequest.EmployeeSnapshot(
                event.getEmpName(), event.getDeptName(), event.getEmpGrade(), event.getEmpTitle());

        VacationGrantRequest request = VacationGrantRequest.createPending(
                event.getCompanyId(), vacationType, employee, snapshot,
                useDays, event.getVacReqReason(), event.getApprovalDocId(), event.getPregnancyWeeks());
        VacationGrantRequest saved = vacationGrantRequestRepository.save(request);

        log.info("[VacationGrantRequest] grantDocCreated → INSERT - requestId={}, docId={}, empId={}, typeId={}, useDays={}",
                saved.getRequestId(), saved.getApprovalDocId(), employee.getEmpId(),
                vacationType.getTypeId(), useDays);
    }

    /* Kafka(vacation-grant-approval-result) 진입 - 상태 전이 + Balance 적립 (APPROVED 만) */
    /* 2층 방어: VacationBalance.@Version 낙관적 락이 동시 accrue UPDATE 충돌 감지 → Kafka retry */
    public void applyApprovalResult(VacationApprovalResultEvent event) {
        VacationGrantRequest request = vacationGrantRequestRepository
                .findByCompanyIdAndRequestId(event.getCompanyId(), event.getVacReqId())
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_REQ_NOT_FOUND));

        RequestStatus newStatus = RequestStatus.valueOf(event.getStatus());
        Employee manager = (event.getManagerId() != null)
                ? employeeRepository.findById(event.getManagerId())
                    .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND))
                : null;

        request.apply(newStatus, manager, event.getRejectReason());

        if (newStatus == RequestStatus.APPROVED) {
            applyApprovedAccrual(request, manager, event.getManagerId());
        }
        // REJECTED 는 Balance 건드린 적 없음 - 상태 전이만으로 종료

        log.info("[VacationGrantRequest] 결재 결과 반영 - requestId={}, status={}, managerId={}",
                request.getRequestId(), newStatus, event.getManagerId());
    }

    /* 사원 셀프 취소 - PENDING/APPROVED 모두 가능 */
    public void cancelByEmployee(UUID companyId, Long empId, Long requestId, String reason) {
        VacationGrantRequest request = loadRequestForCompany(companyId, requestId);
        if (!request.getEmployee().getEmpId().equals(empId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        cancelInternal(request, empId, reason, false);
    }

    /* 관리자 직권 취소 */
    public void cancelByAdmin(UUID companyId, Long managerId, Long requestId, String reason) {
        VacationGrantRequest request = loadRequestForCompany(companyId, requestId);
        cancelInternal(request, managerId, reason, true);
    }

    /* 본인 부여 이력 페이지 (createdAt DESC) - 응답 DTO 매핑 */
    @Transactional(readOnly = true)
    public Page<VacationGrantRequestResponse> listMine(UUID companyId, Long empId, Pageable pageable) {
        return vacationGrantRequestRepository.findEmployeeHistory(companyId, empId, pageable)
                .map(VacationGrantRequestResponse::from);
    }


    /* === private 헬퍼 === */

    /* APPROVED 시 Balance 적립 */
    /* balanceYear = 승인 시점 연도 (사용자 결정). 적립 후 request.markApplied() 로 year 기록 */
    /* expiresAt = FAMILY_CARE 만 12/31, 나머지는 null=무기한 */
    /* 3층 방어: cap 초과 시 자동 REJECT 전환 (race 가 1·2층 뚫었을 때 최종 가드) */
    private void applyApprovedAccrual(VacationGrantRequest request, Employee manager, Long managerId) {
        StatutoryVacationType statutoryType = StatutoryVacationType.fromCode(request.getVacationType().getTypeCode());
        int balanceYear = LocalDate.now().getYear();
        LocalDate grantedAt = LocalDate.now();
        LocalDate expiresAt = resolveExpiresAt(statutoryType, balanceYear);

        VacationBalance balance = vacationBalanceRepository
                .findOne(request.getCompanyId(),
                         request.getEmployee().getEmpId(),
                         request.getVacationType().getTypeId(),
                         balanceYear)
                .orElseGet(() -> vacationBalanceRepository.save(
                        VacationBalance.createNew(request.getCompanyId(), request.getVacationType(),
                                request.getEmployee(), balanceYear, grantedAt, expiresAt)));

        BigDecimal useDays = request.getRequestUseDays();
        BigDecimal cap = resolveCap(statutoryType);
        BigDecimal beforeTotal = balance.getTotalDays();

        try {
            balance.accrue(useDays, cap);   // race 상황에서만 cap 초과 가능
        } catch (CustomException e) {
            if (e.getErrorCode() == ErrorCode.VACATION_BALANCE_CAP_EXCEEDED) {
                // 결재자 승인 의사를 시스템이 자동 반려로 전환
                request.applyByAdmin(RequestStatus.REJECTED, manager,
                        "법정 한도(" + cap + "일) 초과로 자동 반려");
                log.warn("[VacationGrantRequest] cap 초과 자동 반려 - requestId={}, useDays={}, cap={}",
                        request.getRequestId(), useDays, cap);
                return;
            }
            throw e;
        }

        request.markApplied(balanceYear);   // 적립 성공 시점에 year 기록 (cancel 추적용)

        BigDecimal afterTotal = balance.getTotalDays();
        vacationLedgerRepository.save(VacationLedger.ofGrantApproved(
                balance, useDays, beforeTotal, afterTotal,
                request.getRequestId(), managerId, "법정휴가 부여 신청 승인"));
    }

    /* 취소 공통 로직 */
    /* PENDING→CANCELED: Balance 미변경 (no-op) */
    /* APPROVED→CANCELED: appliedBalanceYear 의 Balance rollbackAccrual + Ledger GRANT_REVOKED */
    /*   단 APPROVED 이후 USE 로 사용된 적 있으면 차단 (used > total - useDays 검사) */
    private void cancelInternal(VacationGrantRequest request, Long actorId, String reason, boolean isAdmin) {
        RequestStatus currentStatus = request.getRequestStatus();   // apply 전 캡처
        Employee actor = employeeRepository.findById(actorId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        if (isAdmin) {
            request.applyByAdmin(RequestStatus.CANCELED, actor, reason);
        } else {
            request.apply(RequestStatus.CANCELED, actor, reason);
        }

        if (currentStatus == RequestStatus.PENDING) {
            log.info("[VacationGrantRequest] PENDING 취소 - requestId={}, actorId={}, isAdmin={}",
                    request.getRequestId(), actorId, isAdmin);
            return;
        }

        if (currentStatus != RequestStatus.APPROVED) {
            // REJECTED/CANCELED 에서 호출되면 request.apply 단계에서 이미 차단됨 (이중 방어)
            return;
        }

        // APPROVED 취소: 적립 시 사용한 정확한 year 의 Balance 롤백
        Integer balanceYear = request.getAppliedBalanceYear();
        if (balanceYear == null) {
            // 적립 시 year 기록이 없으면 정합성 깨진 상태 (이론상 발생 안 함 - applyApprovedAccrual 누락 의심)
            throw new CustomException(ErrorCode.VACATION_BALANCE_INSUFFICIENT);
        }

        VacationBalance balance = vacationBalanceRepository
                .findOne(request.getCompanyId(),
                         request.getEmployee().getEmpId(),
                         request.getVacationType().getTypeId(),
                         balanceYear)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_BALANCE_INSUFFICIENT));

        BigDecimal useDays = request.getRequestUseDays();
        // 이미 USE 로 사용된 양 만큼은 롤백 불가 - used > (total - useDays) 면 차단
        if (balance.getUsedDays().compareTo(balance.getTotalDays().subtract(useDays)) > 0) {
            throw new CustomException(ErrorCode.VACATION_BALANCE_USED_INSUFFICIENT);
        }

        BigDecimal beforeTotal = balance.getTotalDays();
        balance.rollbackAccrual(useDays);
        BigDecimal afterTotal = balance.getTotalDays();

        vacationLedgerRepository.save(VacationLedger.ofGrantRollback(
                balance, useDays, beforeTotal, afterTotal, request.getRequestId(), actorId, reason));

        log.info("[VacationGrantRequest] APPROVED 취소 - requestId={}, rollback={}, year={}, actorId={}",
                request.getRequestId(), useDays, balanceYear, actorId);
    }

    /* cap 검증 - (현재 Balance.total) + (같은 연도 PENDING 누적) + (이번 요청) ≤ cap */
    /* cap 이 null (MISCARRIAGE/OFFICIAL_LEAVE/커스텀) 이면 skip */
    private void validateCap(UUID companyId, Long empId, VacationType vacationType,
                             StatutoryVacationType statutoryType, BigDecimal useDays) {
        BigDecimal cap = resolveCap(statutoryType);
        if (cap == null) return;

        int year = LocalDate.now().getYear();
        LocalDateTime yearStart = LocalDateTime.of(year, 1, 1, 0, 0);
        LocalDateTime nextYearStart = yearStart.plusYears(1);

        BigDecimal currentTotal = vacationBalanceRepository
                .findOne(companyId, empId, vacationType.getTypeId(), year)
                .map(VacationBalance::getTotalDays)
                .orElse(BigDecimal.ZERO);

        BigDecimal pendingSum = vacationGrantRequestRepository.sumDaysByStatuses(
                companyId, empId, vacationType.getTypeId(),
                List.of(RequestStatus.PENDING), yearStart, nextYearStart);
        if (pendingSum == null) pendingSum = BigDecimal.ZERO;

        BigDecimal projected = currentTotal.add(pendingSum).add(useDays);
        if (projected.compareTo(cap) > 0) {
            throw new CustomException(ErrorCode.VACATION_BALANCE_CAP_EXCEEDED);
        }
    }

    /* enum.defaultDays → BigDecimal cap. 없으면 null (cap 검증 skip) */
    private BigDecimal resolveCap(StatutoryVacationType type) {
        if (type == null || type.getDefaultDays() == null) return null;
        return BigDecimal.valueOf(type.getDefaultDays());
    }

    /* 만료일 결정 - FAMILY_CARE 만 연말 만료, 나머지는 무기한 */
    private LocalDate resolveExpiresAt(StatutoryVacationType type, int balanceYear) {
        if (type == StatutoryVacationType.FAMILY_CARE) {
            return LocalDate.of(balanceYear, 12, 31);
        }
        return null;
    }

    /* 회사 + requestId 단건 조회 - 타 회사 차단 */
    private VacationGrantRequest loadRequestForCompany(UUID companyId, Long requestId) {
        return vacationGrantRequestRepository.findByCompanyIdAndRequestId(companyId, requestId)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_REQ_NOT_FOUND));
    }
}

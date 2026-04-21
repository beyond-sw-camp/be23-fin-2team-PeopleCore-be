package com.peoplecore.vacation.service;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.event.VacationApprovalDocCreatedEvent;
import com.peoplecore.event.VacationApprovalResultEvent;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.dto.VacationAdminPeriodResponse;
import com.peoplecore.vacation.dto.VacationRequestResponse;
import com.peoplecore.vacation.entity.GrantMode;
import com.peoplecore.vacation.entity.OfficialLeaveReason;
import com.peoplecore.vacation.entity.RequestStatus;
import com.peoplecore.vacation.entity.StatutoryVacationType;
import com.peoplecore.vacation.entity.VacationBalance;
import com.peoplecore.vacation.entity.VacationLedger;
import com.peoplecore.vacation.entity.VacationRequest;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationBalanceRepository;
import com.peoplecore.vacation.repository.VacationLedgerRepository;
import com.peoplecore.vacation.repository.VacationRequestQueryRepository;
import com.peoplecore.vacation.repository.VacationRequestRepository;
import com.peoplecore.vacation.repository.VacationTypeRepository;
import com.peoplecore.vacation.util.MiscarriageLeaveRule;
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

/* 휴가 신청 서비스 - Kafka 진입 + 조회 + 취소 */
/* 휴가 유형 grantMode(SCHEDULED / EVENT_BASED) 에 따라 Balance 취급 분기 */
/*   SCHEDULED (연차/월차/생리): 기존 markPending→consume→restore 플로우 (상태 패턴 RequestStatus 위임) */
/*   EVENT_BASED (출산/배우자출산/유산사산/가족돌봄/공가): 신청 시 Balance 무변경, 승인 시 accrue+consumeDirectly */
@Service
@Slf4j
@Transactional
public class VacationRequestService {

    /* 배우자출산휴가 사용 가능 기한 - 출산일 기준 90일 */
    private static final int SPOUSE_BIRTH_WINDOW_DAYS = 90;

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

    /* Kafka(vacation-approval-doc-created) 진입 - PENDING INSERT (grantMode 분기) */
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

        // 성별 제한 검증 - GenderLimit.allows() 로 통과 여부 판정
        if (!vacationType.getGenderLimit().allows(employee.getEmpGender())) {
            throw new CustomException(ErrorCode.VACATION_TYPE_GENDER_NOT_ALLOWED);
        }

        // StatutoryVacationType 매핑 - 커스텀 유형(null) 은 SCHEDULED 취급 (기존 로직 호환)
        StatutoryVacationType statutoryType = StatutoryVacationType.fromCode(vacationType.getTypeCode());
        GrantMode grantMode = (statutoryType != null) ? statutoryType.getGrantMode() : GrantMode.SCHEDULED;

        // 이벤트 기반 메타 검증 + 일수 보정 (유산사산 주수별 자동 산정 등)
        BigDecimal useDays = event.getVacReqUseDay();
        if (grantMode == GrantMode.EVENT_BASED) {
            useDays = validateAndResolveEventMeta(statutoryType, event, useDays);
        }

        // grantMode 별 Balance 처리
        if (grantMode == GrantMode.SCHEDULED) {
            // 스케줄러형 - 기존 Balance 에서 markPending (Balance 없으면 INSUFFICIENT)
            Integer balanceYear = event.getVacReqStartat().toLocalDate().getYear();
            VacationBalance balance = vacationBalanceRepository
                    .findOne(event.getCompanyId(), employee.getEmpId(), vacationType.getTypeId(), balanceYear)
                    .orElseThrow(() -> new CustomException(ErrorCode.VACATION_BALANCE_INSUFFICIENT));
            balance.markPending(useDays);
        } else {
            // 이벤트 기반 - Balance 건드리지 않고 한도 예상 검증만
            validateEventBasedCap(event.getCompanyId(), employee.getEmpId(), vacationType,
                    statutoryType, event.getVacReqStartat(), useDays);
        }

        VacationRequest.EmployeeSnapshot snapshot = new VacationRequest.EmployeeSnapshot(
                event.getEmpName(), event.getDeptName(), event.getEmpGrade(), event.getEmpTitle());

        VacationRequest.EventMeta eventMeta = new VacationRequest.EventMeta(
                event.getProofFileUrl(),
                event.getPregnancyWeeks(),
                event.getOfficialLeaveReason() != null ? OfficialLeaveReason.valueOf(event.getOfficialLeaveReason()) : null,
                event.getRelatedBirthDate());

        VacationRequest request = VacationRequest.createPending(
                event.getCompanyId(), vacationType, employee, snapshot,
                event.getVacReqStartat(), event.getVacReqEndat(), useDays, event.getVacReqReason(),
                event.getApprovalDocId(), eventMeta);
        VacationRequest saved = vacationRequestRepository.save(request);

        log.info("[VacationRequest] docCreated → INSERT - requestId={}, docId={}, empId={}, typeId={}, grantMode={}, useDays={}",
                saved.getRequestId(), saved.getApprovalDocId(), employee.getEmpId(),
                vacationType.getTypeId(), grantMode, useDays);
    }

    /* Kafka(vacation-approval-result) 진입 - 상태 전이 + Balance 반영 (grantMode 분기) */
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

        StatutoryVacationType statutoryType = StatutoryVacationType.fromCode(request.getVacationType().getTypeCode());
        GrantMode grantMode = (statutoryType != null) ? statutoryType.getGrantMode() : GrantMode.SCHEDULED;

        if (grantMode == GrantMode.SCHEDULED) {
            // 스케줄러형 - 상태 패턴 위임 (pending→used or releasePending)
            applyScheduledResult(request, newStatus, event.getManagerId());
        } else {
            // 이벤트 기반 - APPROVED 시 accrue+consumeDirectly, REJECTED 는 no-op
            applyEventBasedResult(request, newStatus, event.getManagerId(), statutoryType);
        }

        log.info("[VacationRequest] 결재 결과 반영 - requestId={}, status={}, grantMode={}, managerId={}",
                request.getRequestId(), newStatus, grantMode, event.getManagerId());
    }

    /* 스케줄러형 결재 결과 - 기존 상태 패턴 그대로 */
    private void applyScheduledResult(VacationRequest request, RequestStatus newStatus, Long managerId) {
        Integer balanceYear = request.getRequestStartAt().toLocalDate().getYear();
        VacationBalance balance = vacationBalanceRepository
                .findOne(request.getCompanyId(),
                         request.getEmployee().getEmpId(),
                         request.getVacationType().getTypeId(),
                         balanceYear)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_BALANCE_INSUFFICIENT));

        Optional<VacationLedger> ledgerToSave = newStatus.applyKafkaResult(
                balance, request.getRequestUseDays(), request.getRequestId(), managerId);
        ledgerToSave.ifPresent(vacationLedgerRepository::save);
    }

    /* 이벤트 기반 결재 결과 - APPROVED 시 Balance 신규 적립+소비, REJECTED 는 상태만 변경 */
    /* accrue 의 cap 인자는 enum.defaultDays (null 허용) - 동시 요청 race 방지 이중 검증 */
    private void applyEventBasedResult(VacationRequest request, RequestStatus newStatus,
                                       Long managerId, StatutoryVacationType statutoryType) {
        if (newStatus != RequestStatus.APPROVED) {
            // REJECTED 는 Balance 건드린 적 없으니 no-op. 상태 전이만으로 종료
            return;
        }

        Integer balanceYear = request.getRequestStartAt().toLocalDate().getYear();
        LocalDate startDate = request.getRequestStartAt().toLocalDate();
        // 가족돌봄만 연말 만료 대상 - 기존 BalanceExpiryScheduler(expires_at <= 오늘) 가 자동 처리
        // 나머지 이벤트 유형은 기간 단위 소진이라 expiresAt null (만료 스케줄러가 건드리지 않음)
        LocalDate expiresAt = (statutoryType == StatutoryVacationType.FAMILY_CARE)
                ? LocalDate.of(balanceYear, 12, 31) : null;

        VacationBalance balance = vacationBalanceRepository
                .findOne(request.getCompanyId(),
                         request.getEmployee().getEmpId(),
                         request.getVacationType().getTypeId(),
                         balanceYear)
                .orElseGet(() -> vacationBalanceRepository.save(
                        VacationBalance.createNew(request.getCompanyId(), request.getVacationType(),
                                request.getEmployee(), balanceYear, startDate, expiresAt)));

        BigDecimal useDays = request.getRequestUseDays();
        BigDecimal cap = resolveCap(statutoryType);

        BigDecimal beforeTotal = balance.getTotalDays();
        balance.accrue(useDays, cap);          // total += useDays (cap 초과 시 예외)
        balance.consumeDirectly(useDays);      // used += useDays (pending 경유 없이 바로 확정)
        BigDecimal afterTotal = balance.getTotalDays();

        // INITIAL_GRANT 이벤트로 기록 - EVENT_BASED 승인 구간의 total 변동 기록
        vacationLedgerRepository.save(VacationLedger.ofEventGrant(
                balance, useDays, beforeTotal, afterTotal,
                request.getRequestId(), managerId, "이벤트 기반 휴가 승인 적립"));
        // used 이동 기록 - ofUsed 는 debit(- 부호) 이벤트
        vacationLedgerRepository.save(VacationLedger.ofUsed(
                balance, useDays, afterTotal, afterTotal,
                request.getRequestId(), managerId));
    }

    /* 내 신청 이력 페이지 조회 - Type fetch join */
    @Transactional(readOnly = true)
    public Page<VacationRequestResponse> listMine(UUID companyId, Long empId, Pageable pageable) {
        return vacationRequestQueryRepository
                .findEmployeeHistory(companyId, empId, pageable)
                .map(VacationRequestResponse::from);
    }

    /* 전사 휴가 관리 - 기간 교집합 + 상태 복수 필터 페이지 조회 */
    /* 사용 예: 2026-04-01 ~ 2026-04-05 기간에 걸친 PENDING/APPROVED 신청들 */
    /* statuses null or 빈 배열 이면 상태 필터 없이 전체 */
    /* 경계 포함: startDate 00:00 ~ endDate 23:59:59 */
    @Transactional(readOnly = true)
    public Page<VacationAdminPeriodResponse> listForAdminByPeriod(UUID companyId,
                                                                  LocalDate startDate,
                                                                  LocalDate endDate,
                                                                  List<RequestStatus> statuses,
                                                                  Pageable pageable) {
        LocalDateTime periodStart = startDate.atStartOfDay();
        LocalDateTime periodEnd = endDate.atTime(23, 59, 59);

        return vacationRequestQueryRepository
                .findByCompanyAndPeriodAndStatuses(companyId, periodStart, periodEnd, statuses, pageable)
                .map(VacationAdminPeriodResponse::from);
    }

    /* 관리자 상태별 조회 페이지 - Type + Employee fetch join */
    @Transactional(readOnly = true)
    public Page<VacationRequestResponse> listForAdmin(UUID companyId, RequestStatus status, Pageable pageable) {
        return vacationRequestQueryRepository
                .findByCompanyAndStatus(companyId, status, pageable)
                .map(VacationRequestResponse::from);
    }

    /* 사원 셀프 취소 - 본인 request 검증 + 정상 전이 규칙 적용 */
    public void cancelByEmployee(UUID companyId, Long empId, Long requestId, String reason) {
        VacationRequest request = loadRequestForCompany(companyId, requestId);
        if (!request.getEmployee().getEmpId().equals(empId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        cancelInternal(request, empId, reason, false);
    }

    /* 관리자 직권 취소 - 규칙 우회 (applyByAdmin) */
    public void cancelByAdmin(UUID companyId, Long managerId, Long requestId, String reason) {
        VacationRequest request = loadRequestForCompany(companyId, requestId);
        cancelInternal(request, managerId, reason, true);
    }

    /* 공통 취소 로직 - grantMode 분기 */
    /*   SCHEDULED: 상태 패턴 cancelFrom 위임 (releasePending or restore) */
    /*   EVENT_BASED: PENDING→CANCELED no-op / APPROVED→CANCELED restore + rollbackAccrual */
    private void cancelInternal(VacationRequest request, Long actorId, String reason, boolean isAdmin) {
        RequestStatus currentStatus = request.getRequestStatus();   // apply 호출 전 캡처 - cancelFrom 호출 위해 원본 필요

        Employee actor = employeeRepository.findById(actorId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        if (isAdmin) {
            request.applyByAdmin(RequestStatus.CANCELED, actor, reason);
        } else {
            request.apply(RequestStatus.CANCELED, actor, reason);
        }

        StatutoryVacationType statutoryType = StatutoryVacationType.fromCode(request.getVacationType().getTypeCode());
        GrantMode grantMode = (statutoryType != null) ? statutoryType.getGrantMode() : GrantMode.SCHEDULED;

        if (grantMode == GrantMode.SCHEDULED) {
            cancelScheduled(request, currentStatus, actorId, reason);
        } else {
            cancelEventBased(request, currentStatus, actorId, reason);
        }

        log.info("[VacationRequest] 취소 완료 - requestId={}, from={}, actorId={}, isAdmin={}, grantMode={}",
                request.getRequestId(), currentStatus, actorId, isAdmin, grantMode);
    }

    /* 스케줄러형 취소 - 상태 패턴 cancelFrom 위임 */
    private void cancelScheduled(VacationRequest request, RequestStatus from, Long actorId, String reason) {
        Integer balanceYear = request.getRequestStartAt().toLocalDate().getYear();
        VacationBalance balance = vacationBalanceRepository
                .findOne(request.getCompanyId(),
                         request.getEmployee().getEmpId(),
                         request.getVacationType().getTypeId(),
                         balanceYear)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_BALANCE_INSUFFICIENT));

        Optional<VacationLedger> ledgerToSave = from.cancelFrom(
                balance, request.getRequestUseDays(), request.getRequestId(), actorId, reason);
        ledgerToSave.ifPresent(vacationLedgerRepository::save);
    }

    /* 이벤트 기반 취소 - PENDING 은 no-op, APPROVED 는 restore + rollbackAccrual */
    private void cancelEventBased(VacationRequest request, RequestStatus from, Long actorId, String reason) {
        if (from == RequestStatus.PENDING) {
            // 신청 시 Balance 건드린 적 없음 → 취소도 무변경
            return;
        }
        if (from != RequestStatus.APPROVED) {
            // 종결 상태(REJECTED/CANCELED) 에서 호출되면 request.apply 단계에서 이미 차단됨 (이중 방어)
            return;
        }

        Integer balanceYear = request.getRequestStartAt().toLocalDate().getYear();
        VacationBalance balance = vacationBalanceRepository
                .findOne(request.getCompanyId(),
                         request.getEmployee().getEmpId(),
                         request.getVacationType().getTypeId(),
                         balanceYear)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_BALANCE_INSUFFICIENT));

        BigDecimal useDays = request.getRequestUseDays();
        BigDecimal beforeTotal = balance.getTotalDays();
        balance.restore(useDays);           // used -= days (가용으로 복귀)
        balance.rollbackAccrual(useDays);   // total -= days (적립분 롤백)
        BigDecimal afterTotal = balance.getTotalDays();

        vacationLedgerRepository.save(VacationLedger.ofRestored(
                balance, useDays, beforeTotal, afterTotal, request.getRequestId(), actorId, reason));
    }

    /* 회사 + requestId 단건 조회 - 타 회사 차단 */
    private VacationRequest loadRequestForCompany(UUID companyId, Long requestId) {
        return vacationRequestRepository.findByCompanyIdAndRequestId(companyId, requestId)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_REQ_NOT_FOUND));
    }

    /* ====== 이벤트 기반 메타 검증 & 보정 ====== */

    /* 이벤트 기반 유형별 필수 메타 검증 + 일수 보정 */
    /* 유산사산: 주수 필수 + 주수→일수 자동 산정 (event.useDay 무시하고 계산값 사용) */
    /* 배우자출산: 출산일 필수 + 90일 이내 검증 + 증빙 필수 */
    /* 출산전후: 증빙 필수 */
    /* 공가: 사유 필수 + 증빙 필수 */
    /* 가족돌봄: 별도 필수 메타 없음 */
    /* 반환: 보정된 useDays (유산사산만 변경, 나머진 입력값 그대로) */
    private BigDecimal validateAndResolveEventMeta(StatutoryVacationType type,
                                                   VacationApprovalDocCreatedEvent event,
                                                   BigDecimal useDays) {
        switch (type) {
            case MISCARRIAGE -> {
                if (event.getPregnancyWeeks() == null) {
                    throw new CustomException(ErrorCode.VACATION_REQ_PREGNANCY_WEEKS_REQUIRED);
                }
                // 주수→일수 자동 산정. 요청 일수와 다르면 산정값 우선 (UI 편의성)
                return MiscarriageLeaveRule.daysForWeeks(event.getPregnancyWeeks());
            }
            case SPOUSE_BIRTH -> {
                if (event.getRelatedBirthDate() == null) {
                    throw new CustomException(ErrorCode.VACATION_REQ_BIRTH_DATE_REQUIRED);
                }
                if (isBlank(event.getProofFileUrl())) {
                    throw new CustomException(ErrorCode.VACATION_REQ_PROOF_REQUIRED);
                }
                // 출산일 기준 90일 이내 사용 검증 (휴가 시작일 기준)
                LocalDate startDate = event.getVacReqStartat().toLocalDate();
                LocalDate deadline = event.getRelatedBirthDate().plusDays(SPOUSE_BIRTH_WINDOW_DAYS);
                if (startDate.isAfter(deadline)) {
                    throw new CustomException(ErrorCode.VACATION_REQ_SPOUSE_BIRTH_EXPIRED);
                }
            }
            case MATERNITY -> {
                if (isBlank(event.getProofFileUrl())) {
                    throw new CustomException(ErrorCode.VACATION_REQ_PROOF_REQUIRED);
                }
            }
            case OFFICIAL_LEAVE -> {
                if (isBlank(event.getOfficialLeaveReason())) {
                    throw new CustomException(ErrorCode.VACATION_REQ_OFFICIAL_REASON_REQUIRED);
                }
                if (isBlank(event.getProofFileUrl())) {
                    throw new CustomException(ErrorCode.VACATION_REQ_PROOF_REQUIRED);
                }
            }
            case FAMILY_CARE -> { /* 증빙 여부 회사 재량 - 서버 강제 검증 없음 */ }
            default -> { /* EVENT_BASED 외 유형은 진입 불가 */ }
        }
        return useDays;
    }

    /* 이벤트 기반 한도 예상 검증 (B안) */
    /* (기존 Balance total) + (PENDING/APPROVED 누적 일수) + (이번 요청) ≤ cap */
    /* cap 이 null (유산사산/공가) 이면 검증 skip */
    private void validateEventBasedCap(UUID companyId, Long empId, VacationType vacationType,
                                       StatutoryVacationType statutoryType,
                                       LocalDateTime startAt, BigDecimal useDays) {
        BigDecimal cap = resolveCap(statutoryType);
        if (cap == null) return;

        int year = startAt.toLocalDate().getYear();
        LocalDateTime yearStart = LocalDateTime.of(year, 1, 1, 0, 0);
        LocalDateTime nextYearStart = yearStart.plusYears(1);

        // 기존 Balance total (없으면 0) - accrue 된 모든 적립 이력 합
        BigDecimal currentTotal = vacationBalanceRepository
                .findOne(companyId, empId, vacationType.getTypeId(), year)
                .map(VacationBalance::getTotalDays)
                .orElse(BigDecimal.ZERO);

        // 같은 연도 진행 중(PENDING) + 이미 승인(APPROVED) 요청 일수 합
        // APPROVED 는 이미 Balance.total 에 반영됐으므로 중복되지만, PENDING 은 Balance 미반영이라 별도 집계 필요
        // 간단히: PENDING 만 집계 (APPROVED 는 currentTotal 에 포함되므로 중복 방지)
        BigDecimal pendingSum = vacationRequestRepository.sumDaysByStatuses(
                companyId, empId, vacationType.getTypeId(),
                List.of(RequestStatus.PENDING), yearStart, nextYearStart);
        if (pendingSum == null) pendingSum = BigDecimal.ZERO;

        BigDecimal projected = currentTotal.add(pendingSum).add(useDays);
        if (projected.compareTo(cap) > 0) {
            throw new CustomException(ErrorCode.VACATION_BALANCE_CAP_EXCEEDED);
        }
    }

    /* enum.defaultDays → BigDecimal cap (없으면 null). 유산사산/공가 는 null 반환되어 cap 검증 skip */
    private BigDecimal resolveCap(StatutoryVacationType type) {
        if (type == null || type.getDefaultDays() == null) return null;
        return BigDecimal.valueOf(type.getDefaultDays());
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

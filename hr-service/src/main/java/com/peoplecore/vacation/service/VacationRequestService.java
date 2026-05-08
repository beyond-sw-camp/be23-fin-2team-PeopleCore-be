package com.peoplecore.vacation.service;

import com.peoplecore.attendance.repository.HolidayLookupRepository;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.entity.HolidayType;
import com.peoplecore.entity.Holidays;
import com.peoplecore.event.VacationApprovalDocCreatedEvent;
import com.peoplecore.event.VacationApprovalResultEvent;
import com.peoplecore.event.VacationSlotItem;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.dto.CalendarHolidayDto;
import com.peoplecore.vacation.dto.MyCalendarResponse;
import com.peoplecore.vacation.dto.MyVacationTypeResponseDto;
import com.peoplecore.vacation.dto.VacationAdminPeriodPageResponse;
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
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final HolidayLookupRepository holidayLookupRepository;

    @Autowired
    public VacationRequestService(VacationRequestRepository vacationRequestRepository,
                                  VacationRequestQueryRepository vacationRequestQueryRepository,
                                  VacationTypeRepository vacationTypeRepository,
                                  EmployeeRepository employeeRepository,
                                  VacationBalanceRepository vacationBalanceRepository,
                                  VacationLedgerRepository vacationLedgerRepository,
                                  VacationPolicyRepository vacationPolicyRepository,
                                  VacationBalanceQueryRepository vacationBalanceQueryRepository,
                                  HolidayLookupRepository holidayLookupRepository) {
        this.vacationRequestRepository = vacationRequestRepository;
        this.vacationRequestQueryRepository = vacationRequestQueryRepository;
        this.vacationTypeRepository = vacationTypeRepository;
        this.employeeRepository = employeeRepository;
        this.vacationBalanceRepository = vacationBalanceRepository;
        this.vacationLedgerRepository = vacationLedgerRepository;
        this.vacationPolicyRepository = vacationPolicyRepository;
        this.vacationBalanceQueryRepository = vacationBalanceQueryRepository;
        this.holidayLookupRepository = holidayLookupRepository;
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

        // 멱등/방어 가드: PENDING 만 결재 결과 반영 대상
        // 종결(APPROVED/REJECTED/CANCELED) 상태에서 수신된 이벤트는 모두 skip
        // - 동일 상태 중복 수신, 다른 종결 상태로의 비정상 이벤트, Kafka 재처리 등 흡수
        // - RequestStatus.kafkaTransitionTo(non-PENDING) 의 UnsupportedOperationException 노출 차단
        if (currentStatus != RequestStatus.PENDING) {
            log.warn("[VacationRequest] applyApprovalResult 비정상/중복 수신 skip - docId={}, current={}, requested={}",
                    event.getApprovalDocId(), currentStatus, newStatus);
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

    /* 전사 휴가 관리 - 기간 교집합 + 상태 필터 / 건별 페이지 + 메타 */
    /* 페이지 단위 = 신청 건(사원 중복 허용). uniqueEmployeeCount = 기간 내 휴가자 수(중복 제거) */
    /* statuses 미지정(null/empty) 시 APPROVED 강제 - "실제로 쉬는 사람" UX 기본값 */
    /* 경계 포함: startDate 00:00 ~ endDate 23:59:59 */
    @Transactional(readOnly = true)
    public VacationAdminPeriodPageResponse listForAdminByPeriod(UUID companyId,
                                                                 LocalDate startDate,
                                                                 LocalDate endDate,
                                                                 List<RequestStatus> statuses,
                                                                 Pageable pageable) {
        LocalDateTime periodStart = startDate.atStartOfDay();
        LocalDateTime periodEnd = endDate.atTime(23, 59, 59);

        // 기본값 APPROVED - 반려/취소 건까지 합산되어 오염되는 것 방지
        List<RequestStatus> effectiveStatuses = (statuses == null || statuses.isEmpty())
                ? List.of(RequestStatus.APPROVED)
                : statuses;

        return vacationRequestQueryRepository
                .findByCompanyAndPeriodAndStatuses(companyId, periodStart, periodEnd, effectiveStatuses, pageable);
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

    /* 내 캘린더(월) - 공휴일 + 내 휴가(PENDING/APPROVED) 단일 응답 */
    @Transactional(readOnly = true)
    public MyCalendarResponse getMyCalendar(UUID companyId, Long empId, YearMonth yearMonth) {
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        // 비반복: 인덱스 좁힘 / 반복: 전체 후 month 필터 (FUNCTION 인덱스 회피)
        List<Holidays> oneTime = holidayLookupRepository.findOneTimeInRange(companyId, start, end);
        List<Holidays> repeating = holidayLookupRepository.findAllRepeating(companyId);
        List<CalendarHolidayDto> holidays = mergeHolidays(oneTime, repeating, yearMonth);

        // 월 경계 교집합 [start 00:00, end 23:59:59.999...]
        LocalDateTime periodStart = start.atStartOfDay();
        LocalDateTime periodEnd = end.atTime(LocalTime.MAX);
        List<VacationRequestResponse> myVacations = vacationRequestQueryRepository
                .findMyCalendarVacations(companyId, empId, periodStart, periodEnd)
                .stream().map(VacationRequestResponse::from).toList();

        return MyCalendarResponse.builder()
                .yearMonth(yearMonth)
                .holidays(holidays)
                .myVacations(myVacations)
                .build();
    }

    /* 비반복+반복 합치고 같은 날 NATIONAL 우선 dedup, 날짜 ASC 정렬 */
    private List<CalendarHolidayDto> mergeHolidays(List<Holidays> oneTime,
                                                    List<Holidays> repeating,
                                                    YearMonth ym) {
        Map<LocalDate, CalendarHolidayDto> dedup = new HashMap<>();
        oneTime.forEach(h -> putWithPriority(dedup, h.getDate(), h));
        repeating.stream()
                .filter(h -> h.getDate().getMonthValue() == ym.getMonthValue())
                .forEach(h -> {
                    LocalDate mapped = safeDate(ym.getYear(), ym.getMonthValue(),
                                                h.getDate().getDayOfMonth());
                    if (mapped != null) putWithPriority(dedup, mapped, h);
                });
        List<CalendarHolidayDto> result = new ArrayList<>(dedup.values());
        result.sort(Comparator.comparing(CalendarHolidayDto::getDate));
        return result;
    }

    /* 같은 날 NATIONAL/COMPANY 동시 매치 시 NATIONAL 우선 */
    private void putWithPriority(Map<LocalDate, CalendarHolidayDto> map, LocalDate d, Holidays h) {
        CalendarHolidayDto cur = map.get(d);
        boolean replace = cur == null
                || (cur.getType() != HolidayType.NATIONAL && h.getHolidayType() == HolidayType.NATIONAL);
        if (!replace) return;
        map.put(d, CalendarHolidayDto.builder()
                .date(d)
                .name(h.getHolidayName())
                .type(h.getHolidayType())
                .isRepeating(Boolean.TRUE.equals(h.getIsRepeating()))
                .build());
    }

    /* 윤년 2/29 → 평년 매핑 가드 */
    private LocalDate safeDate(int year, int month, int day) {
        try { return LocalDate.of(year, month, day); }
        catch (DateTimeException e) { return null; }
    }

    /* 결재 문서 생성 전 사전 검증 - /internal/vacation/validate-request 진입점 */
    /* 통과 시 void, 실패 시 CustomException - createFromApproval 검증 로직과 동일하나 readOnly */
    /* 사전 동기 차단(여기) + 사후 비동기 검증(createFromApproval) 이중 안전망 구성 */
    @Transactional(readOnly = true)
    public void validateForCreate(UUID companyId, Long empId, Long infoId, List<VacationSlotItem> items) {
        // items 비어있으면 즉시 실패
        if (items == null || items.isEmpty()) {
            throw new CustomException(ErrorCode.VACATION_REQ_ITEMS_EMPTY);
        }
        Employee employee = employeeRepository.findById(empId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
        VacationType vacationType = vacationTypeRepository.findById(infoId)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_TYPE_NOT_FOUND));

        // 성별 제한 (임신/출산 등 한정 유형)
        if (!vacationType.getGenderLimit().allows(employee.getEmpGender())) {
            throw new CustomException(ErrorCode.VACATION_TYPE_GENDER_NOT_ALLOWED);
        }

        BigDecimal totalDays = items.stream()
                .map(VacationSlotItem::getUseDay)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 미리쓰기 허용(연차/월차 + 정책 ON) 이면 잔여 검증 생략
        boolean allowNegative = isAdvanceUseAllowed(companyId, vacationType);
        if (allowNegative) return;

        // 미리쓰기 OFF - Balance row 없음 = 잔여 0 → INSUFFICIENT
        Integer balanceYear = items.get(0).getStartAt().toLocalDate().getYear();
        VacationBalance balance = vacationBalanceRepository
                .findOne(companyId, empId, vacationType.getTypeId(), balanceYear)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_BALANCE_INSUFFICIENT));
        if (balance.getAvailableDays().compareTo(totalDays) < 0) {
            throw new CustomException(ErrorCode.VACATION_BALANCE_INSUFFICIENT);
        }
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

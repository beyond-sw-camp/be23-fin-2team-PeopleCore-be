package com.peoplecore.vacation.service;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.dto.MyVacationStatusResponseDto;
import com.peoplecore.vacation.dto.VacationAdjustmentHistoryResponseDto;
import com.peoplecore.vacation.dto.VacationManualGrantRequest;
import com.peoplecore.vacation.entity.RequestStatus;
import com.peoplecore.vacation.entity.VacationBalance;
import com.peoplecore.vacation.entity.VacationLedger;
import com.peoplecore.vacation.entity.VacationPolicy;
import com.peoplecore.vacation.entity.VacationRequest;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationBalanceQueryRepository;
import com.peoplecore.vacation.repository.VacationBalanceRepository;
import com.peoplecore.vacation.repository.VacationLedgerQueryRepository;
import com.peoplecore.vacation.repository.VacationLedgerRepository;
import com.peoplecore.vacation.repository.VacationPolicyRepository;
import com.peoplecore.vacation.repository.VacationRequestQueryRepository;
import com.peoplecore.vacation.repository.VacationTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/* 휴가 잔여 서비스 - 사원 조회 + 관리자 연차 조정(부여/차감) */
@Service
@Slf4j
@Transactional(readOnly = true)
public class VacationBalanceService {

    private final VacationBalanceRepository vacationBalanceRepository;
    private final VacationBalanceQueryRepository vacationBalanceQueryRepository;
    private final VacationLedgerRepository vacationLedgerRepository;
    private final VacationLedgerQueryRepository vacationLedgerQueryRepository;
    private final VacationTypeRepository vacationTypeRepository;
    private final EmployeeRepository employeeRepository;
    private final VacationPolicyRepository vacationPolicyRepository;
    private final VacationRequestQueryRepository vacationRequestQueryRepository;

    @Autowired
    public VacationBalanceService(VacationBalanceRepository vacationBalanceRepository,
                                  VacationBalanceQueryRepository vacationBalanceQueryRepository,
                                  VacationLedgerRepository vacationLedgerRepository,
                                  VacationLedgerQueryRepository vacationLedgerQueryRepository,
                                  VacationTypeRepository vacationTypeRepository,
                                  EmployeeRepository employeeRepository,
                                  VacationPolicyRepository vacationPolicyRepository,
                                  VacationRequestQueryRepository vacationRequestQueryRepository) {
        this.vacationBalanceRepository = vacationBalanceRepository;
        this.vacationBalanceQueryRepository = vacationBalanceQueryRepository;
        this.vacationLedgerRepository = vacationLedgerRepository;
        this.vacationLedgerQueryRepository = vacationLedgerQueryRepository;
        this.vacationTypeRepository = vacationTypeRepository;
        this.employeeRepository = employeeRepository;
        this.vacationPolicyRepository = vacationPolicyRepository;
        this.vacationRequestQueryRepository = vacationRequestQueryRepository;
    }

    /* 관리자 연차 조정 - 다수 사원 일괄. days 양수=부여, 음수=차감 */
    /* 단일 트랜잭션: 한 사원 실패 시 전체 롤백 (관리자 재시도) */
    @Transactional
    public void grantBulk(UUID companyId, Long managerId, VacationManualGrantRequest request) {
        VacationType type = vacationTypeRepository.findById(request.getTypeId())
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_TYPE_NOT_FOUND));
        /* 타 회사 typeId 차단 */
        if (!type.getCompanyId().equals(companyId)) {
            throw new CustomException(ErrorCode.VACATION_TYPE_NOT_FOUND);
        }
        if (request.getEmpIds() == null || request.getEmpIds().isEmpty()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
        if (request.getDays() == null || request.getDays().signum() == 0) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        Integer targetYear = (request.getYear() != null) ? request.getYear() : LocalDate.now().getYear();
        LocalDate today = LocalDate.now();
        /* 음수 허용 조건 - allowAdvanceUse ON + 연차/월차 유형일 때만 차감 후 total 음수 허용 */
        boolean allowNegative = isAdvanceUseAllowed(companyId, type);

        for (Long empId : request.getEmpIds()) {
            grantSingle(companyId, managerId, empId, type, targetYear, today,
                    request.getDays(), request.getExpiresAt(), request.getReason(), allowNegative);
        }

        log.info("[VacationBalance] 관리자 조정 완료 - companyId={}, managerId={}, typeId={}, days={}, count={}",
                companyId, managerId, type.getTypeId(), request.getDays(), request.getEmpIds().size());
    }

    /* 사원 1명 조정 - balance 조회/생성 + 부호별 엔티티 메서드 + Ledger */
    private void grantSingle(UUID companyId, Long managerId, Long empId, VacationType type,
                             Integer year, LocalDate grantedAt, BigDecimal days,
                             LocalDate expiresAt, String reason, boolean allowNegative) {
        Employee emp = employeeRepository.findById(empId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        VacationBalance balance = vacationBalanceRepository
                .findOne(companyId, empId, type.getTypeId(), year)
                .orElseGet(() -> vacationBalanceRepository.save(
                        VacationBalance.createNew(companyId, type, emp, year, grantedAt, expiresAt)));

        if (days.signum() > 0) {
            /* 부여 - accrue + ofManualGrant */
            BigDecimal before = balance.getTotalDays();
            balance.accrue(days, null);
            BigDecimal after = balance.getTotalDays();
            vacationLedgerRepository.save(VacationLedger.ofManualGrant(
                    balance, days, before, after, managerId, reason));
        } else {
            /* 차감 (관리자 수동 사용 기록) - days 음수. usedDays 증가 (totalDays 불변) */
            /* consumeDirectly 내부에서 allowNegative=false 면 available 검증, true 면 스킵 */
            BigDecimal abs = days.abs();
            BigDecimal before = balance.getTotalDays();
            balance.consumeDirectly(abs, allowNegative);
            BigDecimal after = balance.getTotalDays();
            vacationLedgerRepository.save(VacationLedger.ofManualUsed(
                    balance, abs, before, after, managerId, reason));
        }
    }

    /* allowAdvanceUse 정책 + 연차/월차 유형 동시 만족 시 true (차감 시 total 음수 허용 대상) */
    /* 그 외 (법정휴가 / 정책 OFF / 정책 없음) 는 false - total >= days 검증 유지 */
    private boolean isAdvanceUseAllowed(UUID companyId, VacationType vacationType) {
        if (!vacationType.isAnnual() && !vacationType.isMonthly()) return false;
        return vacationPolicyRepository.findByCompanyId(companyId)
                .map(VacationPolicy::isAdvanceUseActive)
                .orElse(false);
    }

    /* 관리자 수동 조정 이력 조회 - MANUAL_GRANT / MANUAL_USED 만. 스크롤형 Slice */
    /* year / typeId 동적 필터. managerName 은 Employee bulk 조회로 N+1 방지 */
    public Slice<VacationAdjustmentHistoryResponseDto> listAdjustmentHistory(
            UUID companyId, Long empId, Integer year, Long typeId, Pageable pageable) {

        Slice<VacationLedger> slice = vacationLedgerQueryRepository
                .findManualAdjustments(companyId, empId, year, typeId, pageable);

        if (slice.isEmpty()) {
            return slice.map(l -> VacationAdjustmentHistoryResponseDto.from(l, null));
        }

        // 관리자 이름 bulk 조회 - Ledger.managerId 집합 → Map<empId, empName>
        Set<Long> managerIds = slice.getContent().stream()
                .map(VacationLedger::getManagerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> managerNameMap = managerIds.isEmpty()
                ? Map.of()
                : employeeRepository.findAllById(managerIds).stream()
                    .collect(Collectors.toMap(Employee::getEmpId, Employee::getEmpName));

        return slice.map(l -> VacationAdjustmentHistoryResponseDto.from(
                l, managerNameMap.get(l.getManagerId())));
    }

    /* 내 휴가 현황 조회 - 휴가현황 페이지 단일 endpoint */
    /* year 는 프론트 필수 전송 (컨트롤러에서 보장)                                        */
    /* balance_year 컨벤션: AnnualGrantService.grantAndRecord 가 today.getYear() 로 찍음 */
    /*   (HIRE/FISCAL 공통) → year=YYYY = "YYYY 년도에 발생/부여된 balance"               */
    /* HIRE period: grantedAt=기념일, expiresAt=기념일+1년-1일 (달력연도 크로스)          */
    /* FISCAL period: grantedAt=YYYY-01-01, expiresAt=YYYY-12-31                          */
    /* 예정/지난 분류:                                                                    */
    /*   - PENDING → 예정                                                                 */
    /*   - APPROVED + endAt >= now → 예정 (진행중 포함)                                   */
    /*   - APPROVED + endAt < now → 지난                                                  */
    /*   - REJECTED / CANCELED → 지난 (종결)                                              */
    public MyVacationStatusResponseDto getMyVacationStatus(UUID companyId, Long empId, int year) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yearStart = LocalDate.of(year, 1, 1).atStartOfDay();
        LocalDateTime yearEnd = LocalDate.of(year, 12, 31).atTime(23, 59, 59);

        /* balance - year 기준 전체 로드 후 ANNUAL / 기타 분리 */
        List<VacationBalance> balances = vacationBalanceQueryRepository
                .findByEmpAndYearFetchType(companyId, empId, year);

        MyVacationStatusResponseDto.AnnualSummary annual = null;
        VacationBalance monthly = null; // 폴백 후보 (입사 1년 미만)
        List<MyVacationStatusResponseDto.OtherBalance> others = new ArrayList<>();

        for (VacationBalance b : balances) {
            String code = b.getVacationType().getTypeCode();
            if (VacationType.CODE_ANNUAL.equals(code)) {
                /* UNIQUE (company, emp, type, year) → ANNUAL 은 최대 1건 */
                annual = toAnnualSummary(b);
            } else if (VacationType.CODE_MONTHLY.equals(code)) {
                monthly = b; // ANNUAL 없을 때만 annual 로 승격, 있으면 others 로 내림
            } else {
                others.add(MyVacationStatusResponseDto.OtherBalance.from(b));
            }
        }

        /* 입사 1년 미만 - ANNUAL 미생성 상태면 MONTHLY 를 연차 카드 자리에 노출 */
        if (annual == null && monthly != null) {
            annual = toAnnualSummary(monthly);
        } else if (monthly != null) {
            /* 1주년 전환 시점 겹치는 경우 - MONTHLY 는 이력으로 others 에 보여줌 */
            others.add(MyVacationStatusResponseDto.OtherBalance.from(monthly));
        }

        /* request - 연도 overlap 로드 후 now 기준 예정/지난 분류 */
        List<VacationRequest> requests = vacationRequestQueryRepository
                .findByEmpAndYearOverlapFetchType(companyId, empId, yearStart, yearEnd);

        List<MyVacationStatusResponseDto.RequestItem> upcoming = new ArrayList<>();
        List<MyVacationStatusResponseDto.RequestItem> past = new ArrayList<>();

        requests.forEach(r -> {
            RequestStatus s = r.getRequestStatus();
            MyVacationStatusResponseDto.RequestItem item = MyVacationStatusResponseDto.RequestItem.from(r);
            if (s == RequestStatus.REJECTED || s == RequestStatus.CANCELED) {
                past.add(item); // 종결 - 지난
            } else if (s == RequestStatus.PENDING) {
                upcoming.add(item); // 대기 - 항상 예정
            } else { // APPROVED
                if (r.getRequestEndAt().isBefore(now)) {
                    past.add(item);
                } else {
                    upcoming.add(item); // 진행중 포함
                }
            }
        });

        /* 지난: 최근 종료 먼저 */
        past.sort(Comparator.comparing(
                MyVacationStatusResponseDto.RequestItem::getEndAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return MyVacationStatusResponseDto.builder()
                .year(year)
                .annual(annual)
                .others(others)
                .upcoming(upcoming)
                .past(past)
                .build();
    }

    /* 내 예정 휴가 페이지 조회 - 휴가현황 페이지 upcoming 탭 (페이지네이션 분리 endpoint) */
    /* 분류 규칙은 getMyVacationStatus 와 동일. DB 레벨 필터 + 페이징으로 위임 */
    public Page<MyVacationStatusResponseDto.RequestItem> listMyUpcoming(UUID companyId, Long empId,
                                                                        int year, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yearStart = LocalDate.of(year, 1, 1).atStartOfDay();
        LocalDateTime yearEnd = LocalDate.of(year, 12, 31).atTime(23, 59, 59);

        return vacationRequestQueryRepository
                .findUpcomingPage(companyId, empId, yearStart, yearEnd, now, pageable)
                .map(MyVacationStatusResponseDto.RequestItem::from);
    }

    /* 내 지난 휴가 페이지 조회 - 휴가현황 페이지 past 탭 (페이지네이션 분리 endpoint) */
    public Page<MyVacationStatusResponseDto.RequestItem> listMyPast(UUID companyId, Long empId,
                                                                    int year, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yearStart = LocalDate.of(year, 1, 1).atStartOfDay();
        LocalDateTime yearEnd = LocalDate.of(year, 12, 31).atTime(23, 59, 59);

        return vacationRequestQueryRepository
                .findPastPage(companyId, empId, yearStart, yearEnd, now, pageable)
                .map(MyVacationStatusResponseDto.RequestItem::from);
    }

    /* VacationBalance → AnnualSummary 매핑 (ANNUAL/MONTHLY 공용) */
    private MyVacationStatusResponseDto.AnnualSummary toAnnualSummary(VacationBalance b) {
        return MyVacationStatusResponseDto.AnnualSummary.builder()
                .typeCode(b.getVacationType().getTypeCode())
                .typeName(b.getVacationType().getTypeName())
                .periodStart(b.getGrantedAt())
                .periodEnd(b.getExpiresAt())
                .totalDays(b.getTotalDays())
                .usedDays(b.getUsedDays())
                .pendingDays(b.getPendingDays())
                .expiredDays(b.getExpiredDays())
                .availableDays(b.getAvailableDays())
                .build();
    }
}

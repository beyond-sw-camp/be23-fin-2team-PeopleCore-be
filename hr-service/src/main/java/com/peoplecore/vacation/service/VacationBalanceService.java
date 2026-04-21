package com.peoplecore.vacation.service;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.dto.VacationAdjustmentHistoryResponseDto;
import com.peoplecore.vacation.dto.VacationBalanceResponse;
import com.peoplecore.vacation.dto.VacationGrantRequest;
import com.peoplecore.vacation.entity.VacationBalance;
import com.peoplecore.vacation.entity.VacationLedger;
import com.peoplecore.vacation.entity.VacationPolicy;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationBalanceQueryRepository;
import com.peoplecore.vacation.repository.VacationBalanceRepository;
import com.peoplecore.vacation.repository.VacationLedgerQueryRepository;
import com.peoplecore.vacation.repository.VacationLedgerRepository;
import com.peoplecore.vacation.repository.VacationPolicyRepository;
import com.peoplecore.vacation.repository.VacationTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Autowired
    public VacationBalanceService(VacationBalanceRepository vacationBalanceRepository,
                                  VacationBalanceQueryRepository vacationBalanceQueryRepository,
                                  VacationLedgerRepository vacationLedgerRepository,
                                  VacationLedgerQueryRepository vacationLedgerQueryRepository,
                                  VacationTypeRepository vacationTypeRepository,
                                  EmployeeRepository employeeRepository,
                                  VacationPolicyRepository vacationPolicyRepository) {
        this.vacationBalanceRepository = vacationBalanceRepository;
        this.vacationBalanceQueryRepository = vacationBalanceQueryRepository;
        this.vacationLedgerRepository = vacationLedgerRepository;
        this.vacationLedgerQueryRepository = vacationLedgerQueryRepository;
        this.vacationTypeRepository = vacationTypeRepository;
        this.employeeRepository = employeeRepository;
        this.vacationPolicyRepository = vacationPolicyRepository;
    }

    /* 내 잔여 목록 - 해당 연도 모든 유형. Type fetch join 으로 N+1 방지 */
    /* year 생략 시 오늘의 달력 연도 */
    public List<VacationBalanceResponse> listMine(UUID companyId, Long empId, Integer year) {
        Integer targetYear = (year != null) ? year : LocalDate.now().getYear();
        return vacationBalanceQueryRepository
                .findAllByEmpYearFetchType(companyId, empId, targetYear)
                .stream()
                .map(VacationBalanceResponse::from)
                .toList();
    }

    /* 관리자 연차 조정 - 다수 사원 일괄. days 양수=부여, 음수=차감 */
    /* 단일 트랜잭션: 한 사원 실패 시 전체 롤백 (관리자 재시도) */
    @Transactional
    public void grantBulk(UUID companyId, Long managerId, VacationGrantRequest request) {
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
}

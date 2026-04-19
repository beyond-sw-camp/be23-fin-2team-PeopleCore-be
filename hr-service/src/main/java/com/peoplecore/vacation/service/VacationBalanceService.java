package com.peoplecore.vacation.service;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.dto.VacationBalanceResponse;
import com.peoplecore.vacation.dto.VacationGrantRequest;
import com.peoplecore.vacation.entity.VacationBalance;
import com.peoplecore.vacation.entity.VacationLedger;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationBalanceQueryRepository;
import com.peoplecore.vacation.repository.VacationBalanceRepository;
import com.peoplecore.vacation.repository.VacationLedgerRepository;
import com.peoplecore.vacation.repository.VacationTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/* 휴가 잔여 서비스 - 사원 조회 + 관리자 수동 부여 */
/* 부여는 단일 트랜잭션 (한 사원 실패 = 전체 롤백). 관리자 수동 작업이라 사전 검증 전제 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class VacationBalanceService {

    private final VacationBalanceRepository vacationBalanceRepository;
    private final VacationBalanceQueryRepository vacationBalanceQueryRepository;
    private final VacationLedgerRepository vacationLedgerRepository;
    private final VacationTypeRepository vacationTypeRepository;
    private final EmployeeRepository employeeRepository;
    private final VacationBalanceService self;

    @Autowired
    public VacationBalanceService(VacationBalanceRepository vacationBalanceRepository,
                                  VacationBalanceQueryRepository vacationBalanceQueryRepository,
                                  VacationLedgerRepository vacationLedgerRepository,
                                  VacationTypeRepository vacationTypeRepository,
                                  EmployeeRepository employeeRepository, @Lazy VacationBalanceService self) {
        this.vacationBalanceRepository = vacationBalanceRepository;
        this.vacationBalanceQueryRepository = vacationBalanceQueryRepository;
        this.vacationLedgerRepository = vacationLedgerRepository;
        this.vacationTypeRepository = vacationTypeRepository;
        this.employeeRepository = employeeRepository;
        this.self = self;
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

    /* 관리자 부여 - 다수 사원 일괄 */
    /* 단일 트랜잭션: 한 사원 실패 시 전체 롤백 (관리자 재시도 흐름) */
    /* 잔여 row 없으면 createNew 후 accrue + ManualGrant Ledger */
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

        Integer targetYear = (request.getYear() != null) ? request.getYear() : LocalDate.now().getYear();
        LocalDate today = LocalDate.now();

        int success = 0;
        List<Long> failed = new ArrayList<>();

        for (Long empId : request.getEmpIds()) {
            try {
                /* self 경유 - 외부 프록시 호출로 전환되어야 REQUIRES_NEW 적용됨 */
                self.grantSingle(companyId, managerId, empId, type, targetYear, today,
                        request.getDays(), request.getExpiresAt(), request.getReason());
                success++;
            } catch (Exception e) {
                failed.add(empId);
                log.error("[VacationBalance] 사원 부여 실패 - empId={}, err={}",
                        empId, e.getMessage(), e);
            }
        }
        log.info("[VacationBalance] 관리자 부여 완료 - companyId={}, managerId={}, typeId={}, success={}/{}, failed={}",
                companyId, managerId, type.getTypeId(), success, request.getEmpIds().size(), failed);
    }

    /* 사원 1명 부여 - balance 조회/생성 + accrue + Ledger */
    private void grantSingle(UUID companyId, Long managerId, Long empId, VacationType type,
                             Integer year, LocalDate grantedAt, BigDecimal days,
                             LocalDate expiresAt, String reason) {
        Employee emp = employeeRepository.findById(empId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        VacationBalance balance = vacationBalanceRepository
                .findOne(companyId, empId, type.getTypeId(), year)
                .orElseGet(() -> vacationBalanceRepository.save(
                        VacationBalance.createNew(companyId, type, emp, year, grantedAt, expiresAt)));

        BigDecimal before = balance.getTotalDays();
        balance.accrue(days, null);
        BigDecimal after = balance.getTotalDays();

        vacationLedgerRepository.save(VacationLedger.ofManualGrant(
                balance, days, before, after, managerId, reason));
    }
}
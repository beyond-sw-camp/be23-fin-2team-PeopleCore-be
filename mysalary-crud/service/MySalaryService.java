package com.peoplecore.pay.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.cache.MySalaryCacheService;
import com.peoplecore.pay.domain.*;
import com.peoplecore.pay.dto.*;
import com.peoplecore.pay.enums.DepStatus;
import com.peoplecore.pay.enums.PayItemType;
import com.peoplecore.pay.repository.*;
import com.peoplecore.salarycontract.domain.SalaryContract;
import com.peoplecore.salarycontract.domain.SalaryContractDetail;
import com.peoplecore.salarycontract.repository.SalaryContractRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 내 급여 조회 서비스
 * - 급여 정보 + 계좌 + 명세서 + 퇴직금 산정 + 퇴직연금 적립금 조회
 * - Redis 캐싱 적용, QueryDSL 동적 쿼리 활용
 *
 * 파일 위치: pay/service/MySalaryService.java
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class MySalaryService {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private SalaryContractRepository salaryContractRepository;

    @Autowired
    private PayStubsRepository payStubsRepository;

    @Autowired
    private EmpAccountsRepository empAccountsRepository;

    @Autowired
    private EmpRetirementAccountRepository empRetirementAccountRepository;

    @Autowired
    private RetirementRepository retirementRepository;

    @Autowired
    private RetirementPensionDepositsRepository pensionDepositsRepository;

    @Autowired
    private MySalaryQueryRepository mySalaryQueryRepository;

    @Autowired
    private MySalaryCacheService cacheService;

    @Autowired
    private PayItemsRepository payItemsRepository;

    // ═══════════════════════════════════════════════════════
    // 1. 내 급여 정보 조회
    // ═══════════════════════════════════════════════════════

    /**
     * 내 급여 정보 조회 (사원 기본정보 + 연봉 + 고정수당 + 계좌)
     * Redis 캐시 우선 조회 → 미스 시 DB 조회 후 캐싱
     */
    public MySalaryInfoResDto getMySalaryInfo(UUID companyId, Long empId) {
        // 캐시 조회
        Optional<MySalaryInfoResDto> cached = cacheService.getSalaryInfoCache(companyId, empId, MySalaryInfoResDto.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        // DB 조회
        Employee employee = findEmployeeOrThrow(companyId, empId);
        MySalaryInfoResDto result = buildSalaryInfo(employee, companyId);

        // 캐시 저장
        cacheService.cacheSalaryInfo(companyId, empId, result);
        return result;
    }

    /**
     * 급여 정보 조립
     */
    private MySalaryInfoResDto buildSalaryInfo(Employee employee, UUID companyId) {
        MySalaryInfoResDto base = MySalaryInfoResDto.fromEmployee(employee);

        // 최신 연봉 계약 조회
        List<SalaryContract> contracts = salaryContractRepository
                .findByCompanyIdAndEmployee_EmpIdAndDeletedAtIsNullOrderByContractYearDesc(companyId, employee.getEmpId());

        BigDecimal annualSalary = BigDecimal.ZERO;
        Long monthlySalary = 0L;
        List<MySalaryInfoResDto.FixedAllowanceDto> allowances = Collections.emptyList();

        if (!contracts.isEmpty()) {
            SalaryContract latestContract = contracts.get(0);
            annualSalary = latestContract.getTotalAmount();
            monthlySalary = annualSalary.divide(BigDecimal.valueOf(12), 0, RoundingMode.FLOOR).longValue();

            // 고정수당 항목 추출 (재귀)
            if (latestContract.getDetails() != null) {
                allowances = extractAllowances(latestContract.getDetails(), 0, companyId);
            }
        }

        // 계좌 정보
        MySalaryInfoResDto.AccountDto accountDto = empAccountsRepository
                .findByEmployee_EmpIdAndCompany_CompanyId(employee.getEmpId(), companyId)
                .map(acc -> MySalaryInfoResDto.AccountDto.builder()
                        .empAccountId(acc.getEmpAccountId())
                        .bankName(acc.getBankName())
                        .accountNumber(acc.getAccountNumber())
                        .accountHolder(acc.getAccountHolder())
                        .build())
                .orElse(null);

        // 퇴직연금 계좌
        MySalaryInfoResDto.RetirementAccountDto retAccountDto = empRetirementAccountRepository
                .findByEmpIdAndCompany_CompanyId(employee.getEmpId(), companyId)
                .map(ra -> MySalaryInfoResDto.RetirementAccountDto.builder()
                        .retirementAccountId(ra.getRetirementAccountId())
                        .retirementType(ra.getRetirementType().name())
                        .pensionProvider(ra.getPensionProvider())
                        .accountNumber(ra.getAccountNumber())
                        .build())
                .orElse(null);

        return MySalaryInfoResDto.builder()
                .empId(base.getEmpId())
                .empName(base.getEmpName())
                .empEmail(base.getEmpEmail())
                .empNum(base.getEmpNum())
                .empPhone(base.getEmpPhone())
                .empType(base.getEmpType())
                .empHireDate(base.getEmpHireDate())
                .deptName(base.getDeptName())
                .gradeName(base.getGradeName())
                .titleName(base.getTitleName())
                .profileImageUrl(base.getProfileImageUrl())
                .annualSalary(annualSalary)
                .monthlySalary(monthlySalary)
                .fixedAllowances(allowances)
                .salaryAccount(accountDto)
                .retirementAccount(retAccountDto)
                .build();
    }

    /**
     * SalaryContractDetail에서 고정수당 항목 추출 (재귀)
     * 기본급(SALARY 카테고리)이 아닌 지급 항목만 필터링
     */
    private List<MySalaryInfoResDto.FixedAllowanceDto> extractAllowances(
            List<SalaryContractDetail> details, int index, UUID companyId) {

        if (index >= details.size()) {
            return new ArrayList<>();
        }

        SalaryContractDetail detail = details.get(index);
        List<MySalaryInfoResDto.FixedAllowanceDto> rest = extractAllowances(details, index + 1, companyId);

        // 지급 항목 중 기본급이 아닌 항목 = 고정수당
        Optional<PayItems> payItem = payItemsRepository
                .findByPayItemIdAndCompany_CompanyId(detail.getPayItemId(), companyId);

        if (payItem.isPresent()
                && payItem.get().getPayItemType() == PayItemType.PAYMENT
                && payItem.get().getPayItemCategory() != com.peoplecore.pay.enums.PayItemCategory.SALARY) {

            MySalaryInfoResDto.FixedAllowanceDto dto = MySalaryInfoResDto.FixedAllowanceDto.builder()
                    .payItemId(detail.getPayItemId())
                    .payItemName(payItem.get().getPayItemName())
                    .amount(detail.getAmount())
                    .build();

            List<MySalaryInfoResDto.FixedAllowanceDto> result = new ArrayList<>();
            result.add(dto);
            result.addAll(rest);
            return result;
        }

        return rest;
    }

    // ═══════════════════════════════════════════════════════
    // 2. 급(상)여명세서 목록 조회
    // ═══════════════════════════════════════════════════════

    /**
     * 연도별 급여명세서 목록 조회 (Redis 캐싱)
     */
    public List<PayStubListResDto> getPayStubList(UUID companyId, Long empId, String year) {
        // 캐시 조회
        Optional<List<PayStubListResDto>> cached = cacheService.getStubListCache(
                companyId, empId, year, new TypeReference<>() {});
        if (cached.isPresent()) {
            return cached.get();
        }

        // DB 조회 - payYearMonth가 "2026-01" 형식이므로 year prefix로 검색
        List<PayStubs> stubs = payStubsRepository
                .findByEmpIdAndCompany_CompanyIdAndPayYearMonthStartingWithOrderByPayYearMonthDesc(
                        empId, companyId, year);

        List<PayStubListResDto> result = mapStubsToDto(stubs, 0);

        // 캐시 저장
        cacheService.cacheStubList(companyId, empId, year, result);
        return result;
    }

    /**
     * PayStubs → PayStubListResDto 변환 (재귀)
     */
    private List<PayStubListResDto> mapStubsToDto(List<PayStubs> stubs, int index) {
        if (index >= stubs.size()) {
            return new ArrayList<>();
        }

        PayStubListResDto dto = PayStubListResDto.fromEntity(stubs.get(index));
        List<PayStubListResDto> rest = mapStubsToDto(stubs, index + 1);

        List<PayStubListResDto> result = new ArrayList<>();
        result.add(dto);
        result.addAll(rest);
        return result;
    }

    // ═══════════════════════════════════════════════════════
    // 3. 급여명세서 상세 조회
    // ═══════════════════════════════════════════════════════

    /**
     * 급여명세서 상세 (지급항목 + 공제항목 분류)
     * QueryDSL로 PayrollDetails + PayItems JOIN 조회
     */
    public PayStubDetailResDto getPayStubDetail(UUID companyId, Long empId, Long payStubId) {
        // 캐시 조회
        Optional<PayStubDetailResDto> cached = cacheService.getStubDetailCache(
                companyId, empId, payStubId, PayStubDetailResDto.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        PayStubs stub = payStubsRepository
                .findByPayStubsIdAndEmpIdAndCompany_CompanyId(payStubId, empId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        Employee employee = findEmployeeOrThrow(companyId, empId);

        // QueryDSL로 항목 조회
        List<PayStubItemResDto> allItems = mySalaryQueryRepository
                .findPayStubItems(stub.getPayrollRunId(), empId, companyId);

        // 지급/공제 분류 (재귀)
        List<PayStubItemResDto> paymentItems = filterByType(allItems, PayItemType.PAYMENT, 0);
        List<PayStubItemResDto> deductionItems = filterByType(allItems, PayItemType.DEDUCTION, 0);

        PayStubDetailResDto result = PayStubDetailResDto.fromEntity(
                stub,
                employee.getEmpName(),
                employee.getDept() != null ? employee.getDept().getDeptName() : null,
                paymentItems,
                deductionItems
        );

        // 캐시 저장
        cacheService.cacheStubDetail(companyId, empId, payStubId, result);
        return result;
    }

    /**
     * 항목 타입별 필터링 (재귀)
     */
    private List<PayStubItemResDto> filterByType(List<PayStubItemResDto> items, PayItemType type, int index) {
        if (index >= items.size()) {
            return new ArrayList<>();
        }

        List<PayStubItemResDto> rest = filterByType(items, type, index + 1);
        PayStubItemResDto current = items.get(index);

        if (current.getPayItemType() == type) {
            List<PayStubItemResDto> result = new ArrayList<>();
            result.add(current);
            result.addAll(rest);
            return result;
        }

        return rest;
    }

    // ═══════════════════════════════════════════════════════
    // 4. 예상 퇴직금 산정
    // ═══════════════════════════════════════════════════════

    /**
     * 근속기준 퇴직금 예상액 계산
     *
     * 퇴직금 = 1일 평균임금 × 30일 × (근속일수 / 365)
     * 1일 평균임금 = (최근 3개월 급여총액 + 상여금 가산액 + 연차수당) / 직전 3개월 총 일수
     * 상여금 가산액 = 직전 1년간 상여금 총액 × (3/12)
     */
    public SeveranceEstimateResDto estimateSeverance(UUID companyId, Long empId, SeveranceEstimateReqDto request) {
        Employee employee = findEmployeeOrThrow(companyId, empId);
        LocalDate hireDate = employee.getEmpHireDate();
        LocalDate resignDate = request.getResignDate();

        // 근속일수
        long serviceDays = ChronoUnit.DAYS.between(hireDate, resignDate);
        if (serviceDays < 365) {
            // 1년 미만 근속 시 퇴직금 없음
            return SeveranceEstimateResDto.builder()
                    .hireDate(hireDate)
                    .resignDate(resignDate)
                    .hasMidSettlement(false)
                    .settlementPeriod(hireDate + " ~ " + resignDate)
                    .serviceDays(serviceDays)
                    .last3MonthTotalDays(0)
                    .last3MonthTotalPay(0L)
                    .lastYearBonusTotal(0L)
                    .annualLeaveAllowance(0L)
                    .avgDailyWage(BigDecimal.ZERO)
                    .estimatedSeverance(0L)
                    .build();
        }

        // 예상 퇴직일 이전 3개월 기간 산출
        YearMonth resignYm = YearMonth.from(resignDate);
        List<String> last3Months = buildMonthRange(resignYm.minusMonths(3), resignYm.minusMonths(1), new ArrayList<>());

        // 직전 3개월 총 일수
        int last3MonthTotalDays = calcTotalDays(resignDate.minusMonths(3), resignDate);

        // 최근 3개월 급여 총액 (QueryDSL)
        Long last3MonthPay = mySalaryQueryRepository.sumRecentMonthsPay(empId, companyId, last3Months);

        // 직전 1년간 상여금 총액 (QueryDSL)
        List<String> last12Months = buildMonthRange(
                resignYm.minusMonths(12), resignYm.minusMonths(1), new ArrayList<>());
        Long lastYearBonus = mySalaryQueryRepository.sumBonusAmount(empId, companyId, last12Months);

        // 상여금 가산액 = 직전 1년 상여금 × (3/12)
        BigDecimal bonusAdded = BigDecimal.valueOf(lastYearBonus)
                .multiply(BigDecimal.valueOf(3))
                .divide(BigDecimal.valueOf(12), 0, RoundingMode.FLOOR);

        // 연차수당 (현재 미산정으로 0 처리, 추후 연차 모듈 연동)
        Long annualLeaveAllowance = 0L;

        // 1일 평균임금
        BigDecimal totalWageBase = BigDecimal.valueOf(last3MonthPay)
                .add(bonusAdded)
                .add(BigDecimal.valueOf(annualLeaveAllowance));

        BigDecimal avgDailyWage = last3MonthTotalDays > 0
                ? totalWageBase.divide(BigDecimal.valueOf(last3MonthTotalDays), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 퇴직금 = 1일 평균임금 × 30 × (근속일수 / 365)
        BigDecimal severance = avgDailyWage
                .multiply(BigDecimal.valueOf(30))
                .multiply(BigDecimal.valueOf(serviceDays))
                .divide(BigDecimal.valueOf(365), 0, RoundingMode.FLOOR);

        return SeveranceEstimateResDto.builder()
                .hireDate(hireDate)
                .resignDate(resignDate)
                .hasMidSettlement(false)
                .settlementPeriod(hireDate + " ~ " + resignDate)
                .serviceDays(serviceDays)
                .last3MonthTotalDays(last3MonthTotalDays)
                .last3MonthTotalPay(last3MonthPay)
                .lastYearBonusTotal(lastYearBonus)
                .annualLeaveAllowance(annualLeaveAllowance)
                .avgDailyWage(avgDailyWage)
                .estimatedSeverance(severance.longValue())
                .build();
    }

    /**
     * 월 범위 문자열 리스트 생성 (재귀)
     * "2026-01", "2026-02", ... 형식
     */
    private List<String> buildMonthRange(YearMonth from, YearMonth to, List<String> acc) {
        if (from.isAfter(to)) {
            return acc;
        }
        acc.add(from.toString());
        return buildMonthRange(from.plusMonths(1), to, acc);
    }

    /**
     * 두 날짜 사이의 일수 계산
     */
    private int calcTotalDays(LocalDate from, LocalDate to) {
        return (int) ChronoUnit.DAYS.between(from, to);
    }

    // ═══════════════════════════════════════════════════════
    // 5. DB/DC 퇴직연금 적립금 조회
    // ═══════════════════════════════════════════════════════

    /**
     * 퇴직연금 적립금 조회 (Redis 캐싱)
     * 회사 퇴직연금 설정(DB/DC) + 사원 퇴직연금 계좌 + 누적 적립금
     */
    public PensionInfoResDto getPensionInfo(UUID companyId, Long empId) {
        // 캐시 조회
        Optional<PensionInfoResDto> cached = cacheService.getPensionInfoCache(
                companyId, empId, PensionInfoResDto.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        Employee employee = findEmployeeOrThrow(companyId, empId);

        // 사원의 퇴직연금 유형 확인 (Employee.retirementType)
        String retirementType = employee.getRetirementType() != null
                ? employee.getRetirementType().name()
                : "severance";

        // 퇴직연금 계좌 조회
        Optional<EmpRetirementAccount> retAccount = empRetirementAccountRepository
                .findByEmpIdAndCompany_CompanyId(empId, companyId);

        // 회사 퇴직연금 설정 조회
        Optional<RetirementSettings> companySettings = retirementRepository
                .findByCompany_CompanyId(companyId);

        // 적립 정보 구성
        PensionInfoResDto result = buildPensionInfo(
                employee, retirementType, retAccount.orElse(null),
                companySettings.orElse(null), companyId);

        // 캐시 저장
        cacheService.cachePensionInfo(companyId, empId, result);
        return result;
    }

    /**
     * 퇴직연금 정보 조립
     */
    private PensionInfoResDto buildPensionInfo(
            Employee employee,
            String retirementType,
            EmpRetirementAccount retAccount,
            RetirementSettings settings,
            UUID companyId) {

        String provider = null;
        String account = null;
        Long monthlyDeposit = null;
        Long totalDeposited = 0L;
        java.time.LocalDateTime lastDepositDate = null;

        // 퇴직연금 운용사/계좌
        if (retAccount != null) {
            provider = retAccount.getPensionProvider();
            account = retAccount.getAccountNumber();
        } else if (settings != null) {
            provider = settings.getPensionProvider();
            account = settings.getPensionAccount();
        }

        // DC형: 월 적립액 = 연봉 / 12
        if ("DC".equals(retirementType)) {
            List<SalaryContract> contracts = salaryContractRepository
                    .findByCompanyIdAndEmployee_EmpIdAndDeletedAtIsNullOrderByContractYearDesc(companyId, employee.getEmpId());
            if (!contracts.isEmpty()) {
                BigDecimal annual = contracts.get(0).getTotalAmount();
                monthlyDeposit = annual.divide(BigDecimal.valueOf(12), 0, RoundingMode.FLOOR).longValue();
            }
        }

        // 누적 적립금액
        totalDeposited = pensionDepositsRepository
                .sumDepositAmountByEmpIdAndCompanyAndStatus(
                        employee.getEmpId(), companyId, DepStatus.COMPLETED);

        // 최근 적립일
        Optional<RetirementPensionDeposits> lastDeposit = pensionDepositsRepository
                .findTopByEmpIdAndCompany_CompanyIdAndDepStatusOrderByDepositDateDesc(
                        employee.getEmpId(), companyId, DepStatus.COMPLETED);
        if (lastDeposit.isPresent()) {
            lastDepositDate = lastDeposit.get().getDepositDate();
        }

        return PensionInfoResDto.builder()
                .pensionType(retirementType)
                .pensionTypeLabel(PensionInfoResDto.toPensionLabel(retirementType))
                .pensionProvider(provider)
                .pensionAccount(account)
                .depositStartDate(employee.getEmpHireDate())
                .lastDepositDate(lastDepositDate)
                .monthlyDeposit(monthlyDeposit)
                .totalDeposited(totalDeposited)
                .build();
    }

    // ═══════════════════════════════════════════════════════
    // 6. 사원 급여 계좌 변경
    // ═══════════════════════════════════════════════════════

    /**
     * 급여 계좌 변경
     */
    @Transactional
    public MySalaryInfoResDto.AccountDto updateSalaryAccount(
            UUID companyId, Long empId, String bankName, String accountNumber, String accountHolder) {

        EmpAccounts account = empAccountsRepository
                .findByEmployee_EmpIdAndCompany_CompanyId(empId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMP_ACCOUNT_NOT_FOUND));

        // EmpAccounts에 update 메서드가 없으므로 새 엔티티로 교체 저장
        Employee employee = findEmployeeOrThrow(companyId, empId);
        EmpAccounts updated = EmpAccounts.builder()
                .empAccountId(account.getEmpAccountId())
                .employee(employee)
                .bankName(bankName)
                .accountNumber(accountNumber)
                .accountHolder(accountHolder)
                .company(employee.getCompany())
                .build();

        EmpAccounts saved = empAccountsRepository.save(updated);

        // 캐시 무효화
        cacheService.evictSalaryInfoCache(companyId, empId);

        return MySalaryInfoResDto.AccountDto.builder()
                .empAccountId(saved.getEmpAccountId())
                .bankName(saved.getBankName())
                .accountNumber(saved.getAccountNumber())
                .accountHolder(saved.getAccountHolder())
                .build();
    }

    // ═══════════════════════════════════════════════════════
    // 공통 헬퍼
    // ═══════════════════════════════════════════════════════

    private Employee findEmployeeOrThrow(UUID companyId, Long empId) {
        return employeeRepository.findByEmpIdAndCompany_CompanyId(empId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
    }
}

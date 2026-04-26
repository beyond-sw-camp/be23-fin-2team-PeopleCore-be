package com.peoplecore.pay.service;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.event.PayrollApprovalResultEvent;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.*;
import com.peoplecore.pay.dtos.*;
import com.peoplecore.pay.dtos.MySalaryInfoResDto.AccountDto;
import com.peoplecore.pay.enums.*;
import com.peoplecore.pay.repository.*;
import com.peoplecore.salarycontract.domain.SalaryContract;
import com.peoplecore.salarycontract.domain.SalaryContractDetail;
import com.peoplecore.salarycontract.repository.SalaryContractDetailRepository;
import com.peoplecore.salarycontract.repository.SalaryContractRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@Transactional(readOnly = true)
public class MySalaryService {

    private final EmployeeRepository employeeRepository;
    private final SalaryContractRepository salaryContractRepository;
    private final SalaryContractDetailRepository salaryContractDetailRepository;
    private final PayItemsRepository payItemsRepository;
    private final EmpAccountsRepository empAccountsRepository;
    private final EmpRetirementAccountRepository empRetirementAccountRepository;
        private final MySalaryCacheService cacheService;
        private final PayStubsRepository payStubsRepository;
        private final MySalaryQueryRepository mySalaryQueryRepository;
        private final RetirementPensionDepositsRepository pensionDepositsRepository;
    private final MySalaryCacheService mySalaryCacheService;
    private final PayrollDetailsRepository payrollDetailsRepository;
    private final RetirementRepository retirementRepository;
    private final EmpSalaryCacheService empSalaryCacheService;

    @Autowired
    public MySalaryService(EmployeeRepository employeeRepository, SalaryContractRepository salaryContractRepository, SalaryContractDetailRepository salaryContractDetailRepository, PayItemsRepository payItemsRepository, EmpAccountsRepository empAccountsRepository, EmpRetirementAccountRepository empRetirementAccountRepository, MySalaryCacheService cacheService, PayStubsRepository payStubsRepository, MySalaryQueryRepository mySalaryQueryRepository, RetirementPensionDepositsRepository pensionDepositsRepository, MySalaryCacheService mySalaryCacheService, PayrollDetailsRepository payrollDetailsRepository, RetirementRepository retirementRepository, EmpSalaryCacheService empSalaryCacheService) {
        this.employeeRepository = employeeRepository;
        this.salaryContractRepository = salaryContractRepository;
        this.salaryContractDetailRepository = salaryContractDetailRepository;
        this.payItemsRepository = payItemsRepository;
        this.empAccountsRepository = empAccountsRepository;
        this.empRetirementAccountRepository = empRetirementAccountRepository;
        this.cacheService = cacheService;
        this.payStubsRepository = payStubsRepository;
        this.mySalaryQueryRepository = mySalaryQueryRepository;
        this.pensionDepositsRepository = pensionDepositsRepository;
        this.mySalaryCacheService = mySalaryCacheService;
        this.payrollDetailsRepository = payrollDetailsRepository;
        this.retirementRepository = retirementRepository;
        this.empSalaryCacheService = empSalaryCacheService;
    }


/// 내 급여 정보 조회 (급여 탭 진입 시)
    /* - 캐시 hit 시 즉시 반환
     * - miss 시 Employee + SalaryContract + EmpAccounts + EmpRetirementAccount 조립 후 캐시 저장 */
    public MySalaryInfoResDto getMySalaryInfo(UUID companyId, Long empId) {
        // 1. 캐시 확인
        MySalaryInfoResDto cached = cacheService.getSalaryInfoCache(
                companyId, empId, MySalaryInfoResDto.class);
        if (cached != null) return cached;

        // 2. 사원 조회
        Employee emp = employeeRepository
                .findByEmpIdAndCompany_CompanyId(empId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        // 3. 급여 정보 조립
        MySalaryInfoResDto.SalaryInfoDto salaryInfo = buildSalaryInfo(companyId, empId);

        // 4. 급여 계좌
        AccountDto salaryAccount = empAccountsRepository
                .findByEmployee_EmpIdAndCompany_CompanyId(empId, companyId)
                .map(this::toAccountDto)
                .orElse(null);

        // 5. 퇴직연금 계좌
        MySalaryInfoResDto.RetirementAccountDto retirementAccount = empRetirementAccountRepository
                .findByEmployee_EmpIdAndCompany_CompanyId(empId, companyId)
                .map(this::toRetirementAccountDto)
                .orElse(null);

//        퇴직연금 계좌/설정
        Optional<EmpRetirementAccount> retAccount = empRetirementAccountRepository.findByEmployee_EmpIdAndCompany_CompanyId(empId, companyId);
        RetirementSettings settings = retirementRepository
                .findByCompany_CompanyId(companyId).orElse(null);
        // effectiveType 계산
        RetirementType effectiveEmpType = null;
        if (settings != null) {
            if (settings.getPensionType() == PensionType.DB_DC) {
                // DB_DC: 사원 본인이 선택. EmpRetirementAccount에 저장된 값 사용.
                effectiveEmpType = retAccount.map(EmpRetirementAccount::getRetirementType).orElse(null);
            } else {
                // 그 외: 회사값을 그대로 매핑
                effectiveEmpType = mapPensionToRetirement(settings.getPensionType());
            }
        }


        // 6. DTO 빌드
        MySalaryInfoResDto result = MySalaryInfoResDto.builder()
                .empId(emp.getEmpId())
                .empName(emp.getEmpName())
                .empEmail(emp.getEmpEmail())
                .empNum(emp.getEmpNum())
                .empPhone(emp.getEmpPhone())
                .empType(emp.getEmpType() != null ? emp.getEmpType() : null)
                .empHireDate(emp.getEmpHireDate())
                .deptName(emp.getDept() != null ? emp.getDept().getDeptName() : null)
                .gradeName(emp.getGrade() != null ? emp.getGrade().getGradeName() : null)
                .titleName(emp.getTitle() != null ? emp.getTitle().getTitleName() : null)
                .profileImageUrl(emp.getEmpProfileImageUrl())
                .salaryInfo(salaryInfo)
                .dependentsCount(emp.getDependentsCount())
                .salaryAccount(salaryAccount)
                .retirementAccount(retirementAccount)
                .empRetirementType(effectiveEmpType)
                .companyPensionType(settings != null ? settings.getPensionType() : null)
                .companyPensionProvider(settings != null ? settings.getPensionProvider() : null)
                .build();

        // 7. 캐시 저장
        cacheService.cacheSalaryInfo(companyId, empId, result);
        return result;
    }
    /** 최신 연봉계약 기반 연봉/월급/고정수당 조립 */
    private MySalaryInfoResDto.SalaryInfoDto buildSalaryInfo(UUID companyId, Long empId) {
        List<SalaryContract> contracts = salaryContractRepository.findByCompanyIdAndEmployee_EmpIdAndDeletedAtIsNullOrderByContractYearDesc(companyId, empId);

        if (contracts.isEmpty()) {
            return MySalaryInfoResDto.SalaryInfoDto.builder()
                    .annualSalary(0L)
                    .monthlySalary(0L)
                    .fixedAllowances(Collections.emptyList())
                    .build();
        }

        SalaryContract latest = contracts.get(0);
        long annual = latest.getTotalAmount() != null ? latest.getTotalAmount().longValue() : 0L;
        long monthly = annual / 12;   // FLOOR

        List<SalaryContractDetail> details = salaryContractDetailRepository
                .findByContract_ContractId(latest.getContractId());

        List<MySalaryInfoResDto.FixedAllowanceDto> allowances = extractAllowances(
                details, 0, companyId, new ArrayList<>());

        return MySalaryInfoResDto.SalaryInfoDto.builder()
                .annualSalary(annual)
                .monthlySalary(monthly)
                .fixedAllowances(allowances)
                .build();
    }

    /**
     * SalaryContractDetail 목록을 순회하며
     * payItemType=PAYMENT AND payItemCategory != SALARY 인 항목만 FixedAllowanceDto 로 매핑.
     * (명세서: 재귀 방식으로 전체 리스트 구성)
     */
    private List<MySalaryInfoResDto.FixedAllowanceDto> extractAllowances(
            List<SalaryContractDetail> details, int index, UUID companyId, List<MySalaryInfoResDto.FixedAllowanceDto> acc) {if (index >= details.size()) return acc;

        SalaryContractDetail detail = details.get(index);
        PayItems payItem = payItemsRepository
                .findByPayItemIdAndCompany_CompanyId(detail.getPayItemId(), companyId)
                .orElse(null);

        if (payItem != null
                && payItem.getPayItemType() == PayItemType.PAYMENT
                && payItem.getPayItemCategory() != PayItemCategory.SALARY) {
            acc.add(MySalaryInfoResDto.FixedAllowanceDto.builder()
                    .payItemId(payItem.getPayItemId())
                    .payItemName(payItem.getPayItemName())
                    .amount(detail.getAmount() != null ? detail.getAmount().longValue() : 0L)
                    .build());
        }

        return extractAllowances(details, index + 1, companyId, acc);
    }

    private AccountDto toAccountDto(EmpAccounts acc) {
        return AccountDto.builder()
                .empAccountId(acc.getEmpAccountId())
                .bankName(acc.getBankName())
                .accountNumber(acc.getAccountNumber())
                .accountHolder(acc.getAccountHolder())
                .build();
    }

    private MySalaryInfoResDto.RetirementAccountDto toRetirementAccountDto(EmpRetirementAccount acc) {
        return MySalaryInfoResDto.RetirementAccountDto.builder()
                .retirementAccountId(acc.getRetirementAccountId())
                .retirementType(acc.getRetirementType() != null ? acc.getRetirementType().name() : null)
                .pensionProvider(acc.getPensionProvider())
                .accountNumber(acc.getAccountNumber())
                .build();
    }


///    연도별 급여명세서 목록
public List<PayStubListResDto> getPayStubList(UUID companyId, Long empId, String year) {
    // 1. 캐시
    List<PayStubListResDto> cached = cacheService.getStubListCache(companyId, empId, year);
    if (cached != null) return cached;

    // 2. DB 조회 — payYearMonth 는 "YYYY-MM" 이므로 startsWith(year) 로 필터
    List<PayStubs> stubs = payStubsRepository.findByEmpIdAndCompany_CompanyIdAndPayYearMonthStartingWithOrderByPayYearMonthDesc(empId, companyId, year);

    // 3. 재귀 변환
    List<PayStubListResDto> result = mapStubsToDto(stubs, 0, new ArrayList<>());

    // 4. 캐시
    cacheService.cacheStubList(companyId, empId, year, result);
    return result;
}

    private List<PayStubListResDto> mapStubsToDto(
            List<PayStubs> stubs, int index, List<PayStubListResDto> acc) {
        if (index >= stubs.size()) return acc;
        PayStubs s = stubs.get(index);
        acc.add(PayStubListResDto.builder()
                .stubId(s.getPayStubsId())
                .payYearMonth(s.getPayYearMonth())
                .totalPay(s.getTotalPay())
                .totalPay(s.getTotalPay())
                .totalDeduction(s.getTotalDeduction())
                .netPay(s.getNetPay())
                .sendStatus(s.getSendStatus() != null ? s.getSendStatus().name() : null)
                .build());
        return mapStubsToDto(stubs, index + 1, acc);
    }


//    급여명세서 상세 (지급/공제 분류)
public PayStubDetailResDto getPayStubDetail(UUID companyId, Long empId, Long stubId) {
    // 캐시
    PayStubDetailResDto cached = cacheService.getStubDetailCache(companyId, empId, stubId);
    if (cached != null) return cached;

    // 본인 소유 검증
    PayStubs stub = payStubsRepository
            .findByPayStubsIdAndEmpIdAndCompany_CompanyId(stubId, empId, companyId)
            .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

    // QueryDSL: PayStubs + PayrollDetails + PayItems 조인 결과
    List<PayStubItemResDto> items = mySalaryQueryRepository.findPayStubItems(stubId);

    // 재귀 분류
    List<PayStubItemResDto> paymentItems = filterByType(
            items, 0, PayItemType.PAYMENT, new ArrayList<>());
    List<PayStubItemResDto> deductionItems = filterByType(
            items, 0, PayItemType.DEDUCTION, new ArrayList<>());

    Employee emp = employeeRepository
            .findByEmpIdAndCompany_CompanyId(empId, companyId)
            .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

    PayStubDetailResDto result = PayStubDetailResDto.builder()
            .stubId(stub.getPayStubsId())
            .payYearMonth(stub.getPayYearMonth())
            .empName(emp.getEmpName())
            .deptName(emp.getDept() != null ? emp.getDept().getDeptName() : null)
            .totalPay(stub.getTotalPay())
            .totalDeduction(stub.getTotalDeduction())
            .netPay(stub.getNetPay())
            .paymentItems(paymentItems)
            .deductionItems(deductionItems)
            .pdfUrl(stub.getPdfUrl())
            .build();

    cacheService.cacheStubDetail(companyId, empId, stubId, result);
    return result;
}

    /**
     * PayStubItem 리스트에서 특정 PayItemType 만 재귀 필터링.
     */
    private List<PayStubItemResDto> filterByType(
            List<PayStubItemResDto> items, int index,
            PayItemType type, List<PayStubItemResDto> acc) {
        if (index >= items.size()) return acc;
        PayStubItemResDto item = items.get(index);
        if (type.name().equals(item.getPayItemType())) {
            acc.add(item);
        }
        return filterByType(items, index + 1, type, acc);
    }

    public PensionInfoResDto getPensionInfo(UUID companyId, Long empId) {
        // 캐시
        PensionInfoResDto cached = cacheService.getPensionCache(companyId, empId);
        if (cached != null) return cached;

        Employee emp = employeeRepository
                .findByEmpIdAndCompany_CompanyId(empId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        String retirementType = emp.getRetirementType() != null
                ? emp.getRetirementType().name()
                : "severance";

        // 최신 연봉 기반 월 적립금 (DC 형만 의미 있음)
        long annual = salaryContractRepository.findByCompanyIdAndEmployee_EmpIdAndDeletedAtIsNullOrderByContractYearDesc(companyId, empId)
                .stream().findFirst()
                .map(c -> c.getTotalAmount() != null ? c.getTotalAmount().longValue() : 0L).orElse(0L);
        long monthlyDeposit = "DC".equals(retirementType) ? annual / 12 : 0L;

        // 누적 적립금 (COMPLETED 합산)
        long totalDeposited = pensionDepositsRepository
                .findByEmployee_EmpIdAndCompany_CompanyIdAndDepStatus(
                        empId, companyId, DepStatus.COMPLETED)
                .stream()
                .mapToLong(d -> d.getDepositAmount() != null ? d.getDepositAmount() : 0L)
                .sum();

        // 최근 적립일
        LocalDateTime lastDepositDate = pensionDepositsRepository
                .findTopByEmployee_EmpIdAndCompany_CompanyIdAndDepStatusOrderByDepositDateDesc(
                        empId, companyId, DepStatus.COMPLETED)
                .map(RetirementPensionDeposits::getDepositDate)
                .orElse(null);

        PensionInfoResDto result = PensionInfoResDto.builder()
                .retirementType(retirementType)
                .monthlyDeposit(monthlyDeposit)
                .totalDeposited(totalDeposited)
                .lastDepositDate(lastDepositDate)
                .build();

        cacheService.cachePensionInfo(companyId, empId, result);
        return result;
    }


    //    부양가족수 변경
    @Transactional
    public void updateMyDependents(UUID companyId, Long empId, Integer dependentsCount){
        Employee emp = employeeRepository.findById(empId)
                .filter(e -> e.getCompany().getCompanyId().equals(companyId))
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        emp.updateDependentsCount(dependentsCount);

        // 캐시 무효화 (내 급여 + 관리자측 둘 다)
        mySalaryCacheService.evictSalaryInfoCache(companyId, empId);
        empSalaryCacheService.evictByEmpId(companyId, empId);
        empSalaryCacheService.evictExpected(companyId);
    }



//    급여 계좌 변경
    @Transactional
    public void updateSalaryAccount(UUID companyId, Long empId, AccountUpdateReqDto req) {
        EmpAccounts existing = empAccountsRepository
                .findByEmployee_EmpIdAndCompany_CompanyId(empId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMP_ACCOUNT_NOT_FOUND));

        // EmpAccounts 는 update 메서드가 없으므로 builder 로 재구성 후 save
        EmpAccounts updated = EmpAccounts.builder()
                .empAccountId(existing.getEmpAccountId())       // 동일 PK → save 시 update
                .employee(existing.getEmployee())
                .company(existing.getCompany())
                .bankName(req.getBankName())
                .accountNumber(req.getAccountNumber())
                .accountHolder(req.getAccountHolder())
                .build();
        empAccountsRepository.save(updated);

        // 캐시 무효화
        cacheService.evictSalaryInfoCache(companyId, empId);
        log.info("[MySalary] 급여 계좌 변경 완료 - empId={}, bank={}", empId, req.getBankName());
    }


//    퇴직연금 타입 설정
    private RetirementType mapPensionToRetirement(PensionType pt) {
        if (pt == null) return null;
        return switch (pt) {
            case DB -> RetirementType.DB;
            case DC -> RetirementType.DC;
            case severance -> RetirementType.severance;
            case DB_DC -> null;   // 호출 측에서 처리
        };
    }
}

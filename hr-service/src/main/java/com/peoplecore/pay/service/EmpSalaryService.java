package com.peoplecore.pay.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.EmpType;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.*;
import com.peoplecore.pay.dtos.*;
import com.peoplecore.pay.enums.PensionType;
import com.peoplecore.pay.enums.RetirementType;
import com.peoplecore.pay.repository.*;
import com.peoplecore.salarycontract.domain.SalaryContract;
import com.peoplecore.salarycontract.domain.SalaryContractDetail;
import com.peoplecore.salarycontract.repository.SalaryContractRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class EmpSalaryService {

    private final EmployeeRepository employeeRepository;
    private final EmpAccountsRepository empAccountsRepository;
    private final SalaryContractRepository salaryContractRepository;
    private final PayItemsRepository payItemsRepository;
    private final EmpRetirementAccountRepository empRetirementAccountRepository;
    private final InsuranceRatesRepository insuranceRatesRepository;
    private final TaxWithholdingService taxWithholdingService;
    private final RetirementRepository retirementRepository;
    private final EmpSalaryCacheService empSalaryCacheService;

    @Autowired
    public EmpSalaryService(EmployeeRepository employeeRepository, EmpAccountsRepository empAccountsRepository, SalaryContractRepository salaryContractRepository, PayItemsRepository payItemsRepository, EmpRetirementAccountRepository empRetirementAccountRepository, InsuranceRatesRepository insuranceRatesRepository, TaxWithholdingService taxWithholdingService, RetirementRepository retirementRepository, EmpSalaryCacheService empSalaryCacheService) {
        this.employeeRepository = employeeRepository;
        this.empAccountsRepository = empAccountsRepository;
        this.salaryContractRepository = salaryContractRepository;
        this.payItemsRepository = payItemsRepository;
        this.empRetirementAccountRepository = empRetirementAccountRepository;
        this.insuranceRatesRepository = insuranceRatesRepository;
        this.taxWithholdingService = taxWithholdingService;
        this.retirementRepository = retirementRepository;
        this.empSalaryCacheService = empSalaryCacheService;
    }

/// 사원 급여 목록
    public Page<EmpSalaryResDto> getEmpSalaryList(UUID companyId, String keyword, Long deptId, EmpType empType, EmpStatus empStatus, Integer year, Pageable pageable) {

//        1. Employee 페이지 조회
        Page<Employee> employees = employeeRepository.findAllWithFilter(companyId, keyword, deptId, empType, empStatus, null, null, pageable);

        if (employees.isEmpty()) {
            return employees.map(employee -> {
                return EmpSalaryResDto.fromEmployee(employee, null, null);
            });
        }
//
        List<Long> empIds = employees.getContent().stream().map(Employee::getEmpId).collect(Collectors.toList());

//        2.사원별 유효 계약 일괄 조회 → Map (N+1 제거)
        Map<Long, SalaryContract> contractMap = buildActiveContractMap(companyId, empIds, year);

//        3. 사원별 계좌 일괄 조회 → Map
        Map<Long, EmpAccounts> accountsMap = empAccountsRepository.findByEmployee_EmpIdInAndCompany_CompanyId(empIds, companyId)
                .stream().collect(Collectors.toMap(
                        a -> a.getEmployee().getEmpId(),
                        Function.identity(),
                        (a, b) -> a //중복시 첫번째
                ));

//        4. DTO 조립
        return employees.map(employee -> {
            SalaryContract contract = contractMap.get(employee.getEmpId());
            EmpAccounts accounts   = accountsMap.get(employee.getEmpId());
            return EmpSalaryResDto.fromEmployee(employee, contract, accounts);
        });
    }


///    급여 상세 (redis cache 사용하여 조회)
    public EmpSalaryDetailResDto getEmpSalaryDetail(UUID companyId, Long empId, Integer year) {

        // 1) 캐시 확인
        EmpSalaryDetailResDto cached = empSalaryCacheService.getDetailCache(companyId, empId, year);
        if (cached != null) return cached;

        // 2) DB 조회
        Employee employee = employeeRepository.findById(empId).orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

//        최신 연봉계약
        Map<Long, SalaryContract> contractMap = buildActiveContractMap(companyId, List.of(empId), year);
        SalaryContract contract = contractMap.get(empId);

        BigDecimal annualSalary = null;
        Long monthlySalary = null;
        List<ContractPayItemResDto> fixedPayItems = List.of();

        if (contract != null) {
            annualSalary = contract.getTotalAmount();
            monthlySalary = annualSalary.divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP).longValue();
            fixedPayItems = buildFixedPayItems(companyId, contract);
        }

//        급여계좌
        Optional<EmpAccounts> empAccount = empAccountsRepository.findByEmployee_EmpIdAndCompany_CompanyId(empId, companyId);
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

        EmpSalaryDetailResDto result = EmpSalaryDetailResDto.builder()
                .empId(employee.getEmpId())
                .empName(employee.getEmpName())
                .empNum(employee.getEmpNum())
                .empEmail(employee.getEmpEmail())
                .empType(employee.getEmpType().name())
                .empStatus(employee.getEmpStatus().name())
                .deptName(employee.getDept().getDeptName())
                .gradeName(employee.getGrade().getGradeName())
                .titleName(employee.getTitle() != null ? employee.getTitle().getTitleName() : null)
                .empHireDate(employee.getEmpHireDate())
                .dependentsCount(employee.getDependentsCount())
                .annualSalary(annualSalary)
                .monthlySalary(monthlySalary)
                .contractStartDate(contract != null ? contract.getApplyFrom() : null)
                .contractEndDate(contract != null ? contract.getApplyTo() : null)
                .fixedPayItems(fixedPayItems)
                .empAccountId(empAccount.map(EmpAccounts::getEmpAccountId).orElse(null))
                .bankName(empAccount.map(EmpAccounts::getBankName).orElse(null))
                .accountNumber(empAccount.map(EmpAccounts::getAccountNumber).orElse(null))
                .accountHolder(empAccount.map(EmpAccounts::getAccountHolder).orElse(null))
                .retirementAccountId(retAccount.map(EmpRetirementAccount::getRetirementAccountId).orElse(null))
                .empRetirementType(effectiveEmpType)
                .pensionProvider(retAccount.map(EmpRetirementAccount::getPensionProvider).orElse(null))
                .retirementAccountNumber(retAccount.map(EmpRetirementAccount::getAccountNumber).orElse(null))
                .companyPensionType(settings != null ? settings.getPensionType() : null)
                .companyPensionProvider(settings != null ? settings.getPensionProvider() : null)
                .empResignDate(employee.getEmpResignDate())
                .build();

        // 3) 캐시 적재
        empSalaryCacheService.cacheDetail(companyId, empId, year, result);
        return result;
    }


    //    부양가족수 변경
    @Transactional
    public void updateDependents(UUID companyId, Long empId, Integer dependentsCount){
        Employee emp = employeeRepository.findById(empId)
                .filter(e -> e.getCompany().getCompanyId().equals(companyId))
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        emp.updateDependentsCount(dependentsCount);

        // 캐시 무효화 (상세 + 예상공제)
        empSalaryCacheService.evictByEmpId(companyId, empId);
        empSalaryCacheService.evictExpected(companyId);
    }


//    급여계좌 변경 (캐시 무효화 evict)
    @Transactional
    public void updateEmpAccount(UUID companyId, Long empId, EmpAccountReqDto reqDto){

        Optional<EmpAccounts> empAccount = empAccountsRepository.findByEmployee_EmpIdAndCompany_CompanyId(empId, companyId);

        if(empAccount.isPresent()){
//            기존 계좌 수정
            empAccount.get().update(
                    reqDto.getBankName(),
                    reqDto.getAccountNumber(),
                    reqDto.getAccountHolder(),
                    reqDto.getBankCode()
            );
        } else {
//            신규 등록
            Employee emp = employeeRepository.findById(empId).orElseThrow(()->new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
            EmpAccounts newAccount = EmpAccounts.builder()
                    .employee(emp)
                    .bankName(reqDto.getBankName())
                    .accountNumber(reqDto.getAccountNumber())
                    .accountHolder(reqDto.getAccountHolder())
                    .company(emp.getCompany())
                    .build();
            empAccountsRepository.save(newAccount);
        }

        // ★ 캐시 무효화
        empSalaryCacheService.evictByEmpId(companyId, empId);
    }

//    퇴직연금계좌 변경
    @Transactional
    public void updateRetirementAccount(UUID companyId, Long empId, EmpRetirementAccountReqDto reqDto){
        Optional<EmpRetirementAccount> retAccount = empRetirementAccountRepository.findByEmployee_EmpIdAndCompany_CompanyId(empId, companyId);

        if (retAccount.isPresent()){
            retAccount.get().update(reqDto.getRetirementType(), reqDto.getPensionProvider(), reqDto.getAccountNumber());
        } else {
            Employee emp = employeeRepository.findById(empId).orElseThrow(()-> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

            EmpRetirementAccount newAccount = EmpRetirementAccount.builder()
                    .retirementType(reqDto.getRetirementType())
                    .pensionProvider(reqDto.getPensionProvider())
                    .accountNumber(reqDto.getAccountNumber())
                    .employee(emp)
                    .company(emp.getCompany())
                    .build();

            empRetirementAccountRepository.save(newAccount);
        }

        // ★ 캐시 무효화
        empSalaryCacheService.evictByEmpId(companyId, empId);
    }

//    퇴직연금 유형 변경 (회사설정 DB_DC 일때만)
    @Transactional
    public void updateRetirementType(UUID companyId, Long empId, RetirementTypeUpdateReqDto reqDto){
        Employee emp = employeeRepository.findById(empId).filter(e -> e.getCompany().getCompanyId().equals(companyId)).orElseThrow(()-> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

//        1. 회사 퇴직연금 설정 조회
        RetirementSettings retirementSettings = retirementRepository.findByCompany_CompanyId(companyId).orElseThrow(()-> new CustomException(ErrorCode.RETIREMENT_SETTINGS_NOT_FOUND));

//      2. 회사 설정이 DB_DC일때만 변경 가능
        if (retirementSettings.getPensionType() != PensionType.DB_DC){
            throw new CustomException(ErrorCode.RETIREMENT_TYPE_NOT_CHANGEABLE);
        }

//        3. 사원이 선택 가능한 값은 DB or DC 만
        if(reqDto.getRetirementType() != RetirementType.DB && reqDto.getRetirementType() != RetirementType.DC){
            throw new CustomException(ErrorCode.INVALID_RETIREMENT_TYPE);
        }

        emp.updateRetirementType(reqDto.getRetirementType());

        // ★ 캐시 무효화
        empSalaryCacheService.evictByEmpId(companyId, empId);
    }



///     월급여 예상지급공제
    public ExpectedDeductionSummaryResDto getExpectedDeductions(UUID companyId) {
        // 1) 캐시 확인
        ExpectedDeductionSummaryResDto cached = empSalaryCacheService.getExpectedCache(companyId);
        if (cached != null) return cached;

        // 2) 기존 그대로
        List<Employee> employees = employeeRepository.findByCompany_CompanyIdAndEmpStatusInAndDeleteAtIsNull(companyId, List.of(EmpStatus.ACTIVE, EmpStatus.ON_LEAVE));

        if (employees.isEmpty()) {
            ExpectedDeductionSummaryResDto empty =  ExpectedDeductionSummaryResDto.builder()
                                                    .totalEmployees(0)
                                                    .totalExpectedNetPay(0L)
                                                    .employees(List.of())
                                                    .build();
            empSalaryCacheService.cacheExpected(companyId, empty);
            return empty;
        }

//        현재 연도 보험요율
        int currentYear = LocalDate.now().getYear();
        InsuranceRates rates = insuranceRatesRepository.findByCompany_CompanyIdAndYear(companyId, currentYear).orElse(null);

//        사원별 최신 연봉계약
        List<Long> empIds = employees.stream().map(Employee::getEmpId).collect(Collectors.toList());
        Map<Long, SalaryContract> contractMap = buildActiveContractMap(companyId, empIds, null);

        List<ExpectedDeductionResDto> result = new ArrayList<>();
//        예상 실수령액 합계
        long grandTotalNet = 0L;

        for (Employee emp : employees) {
            SalaryContract contract = contractMap.get(emp.getEmpId());

            BigDecimal annualSalary = contract != null ? contract.getTotalAmount() : null;
            Long monthlySalary = annualSalary != null ? annualSalary.divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP).longValue() : null;

//            기본급 = 월급 - 고정수당(SalaryContractDetail)
            Long basePay = null;
            if (contract != null && monthlySalary != null) {
                int fixedSum = contract.getDetails() != null ? contract.getDetails().stream().mapToInt(SalaryContractDetail::getAmount).sum() : 0;
                basePay = monthlySalary - fixedSum;
                if (basePay < 0) basePay = 0l;
            }

//            4대보험 + 세금
            Long pension = null, health = null, ltc = null, employment = null;
            Long incomeTax = null, localIncomeTax = null;
            Long totalDeduction = null, expectedNetPay = null;

            if (monthlySalary != null && rates != null) {

//                국민연금 - 상/하한 적용
                long pensionBase = monthlySalary;
                if (pensionBase > rates.getPensionUpperLimit()) {
                    pensionBase = rates.getPensionUpperLimit();
                }
                if (pensionBase < rates.getPensionUpperLimit()) {
                    pensionBase = rates.getPensionLowerLimit();
                }
                pension = calcHalf(pensionBase, rates.getNationalPension());

//                건강보험
                health = calcHalf(monthlySalary, rates.getHealthInsurance());

//                장기요양(건강보험전액 * 요율 / 2)
                long healthTotal = health *2;
                long ltcTotal = calcAmount(healthTotal, rates.getLongTermCare());
                ltc = ltcTotal /2;

//                고용보험(근로자 요율)
                employment = calcAmount(monthlySalary, rates.getEmploymentInsurance());

                log.info("[Tax 디버그] year={}, monthlySalary={}, dependents={}",
                        currentYear, monthlySalary, emp.getDependentsCount());
//                소득세(간이세액표 조회) - 해당구간없으면 0
                TaxWithholdingResDto tax = taxWithholdingService.getTax(currentYear, monthlySalary, emp.getDependentsCount());
                log.info("[Tax 디버그] 결과 incomeTax={}, localIncomeTax={}",
                        tax != null ? tax.getIncomeTax() : "null",
                        tax != null ? tax.getLocalIncomeTax() : "null");
                if (tax != null) {
                    incomeTax = tax.getIncomeTax();
                    localIncomeTax = tax.getLocalIncomeTax();
                } else {
//                    세액표에 해당구간이 없으면 0
                    incomeTax = 0L;
                    localIncomeTax = 0L;
                }

                totalDeduction = pension + health + ltc + employment + incomeTax + localIncomeTax;
                expectedNetPay = monthlySalary - totalDeduction;
            }

            if (expectedNetPay != null){
                grandTotalNet += expectedNetPay;
            }

            result.add(ExpectedDeductionResDto.builder()
                            .empId(emp.getEmpId())
                            .empStatus(emp.getEmpStatus().name())
                            .empName(emp.getEmpName())
                            .deptName(emp.getDept().getDeptName())
                            .titleName(emp.getTitle() != null ? emp.getTitle().getTitleName() : null)
                            .annualSalary(annualSalary)
                            .monthlySalary(monthlySalary)
                            .basePay(basePay)
                            .nationalPension(pension)
                            .healthInsurance(health)
                            .longTermCare(ltc)
                            .employmentInsurance(employment)
                            .incomeTax(incomeTax)
                            .localIncomeTax(localIncomeTax)
                            .totalDeduction(totalDeduction)
                            .expectedNetPay(expectedNetPay)
                    .build());
        }
        ExpectedDeductionSummaryResDto built =  ExpectedDeductionSummaryResDto.builder()
                                                .totalEmployees(employees.size())
                                                .totalExpectedNetPay(grandTotalNet)
                                                .employees(result)
                                                .build();

        // 3) 캐시 적재
        empSalaryCacheService.cacheExpected(companyId, built);
        return built;
    }





//    조회기준 기간 산출  (year 미지정 → 오늘, 지정 → YYYY-01-01 ~ YYYY-12-31)
    private LocalDate[] resolvePeriod(Integer year){
        if (year == null) {
            LocalDate today = LocalDate.now();
            return new LocalDate[]{today, today};
        }
        return new LocalDate[]{
                LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31)
        };
    }

//    사원별 유효 계약 일괄 조회 (N+1 해결).
//    year=null: 오늘 유효한 계약,  year=YYYY: 해당 연도에 적용된 계약(없으면 빠짐)
    private Map<Long, SalaryContract> buildActiveContractMap(UUID companyId, List<Long> empIds, Integer year){
        if (empIds == null || empIds.isEmpty()) return Map.of();

        LocalDate[] period = resolvePeriod(year);
        List<SalaryContract> contracts = salaryContractRepository.findActiveContractsByEmpIds(
                companyId, empIds, period[0], period[1]
        );

        //   SQL이 applyFrom DESC 로 정렬 → empId별 첫 번째가 최신. (같은연도중에서도 최근의 계약건 조회시 사용)
        return contracts.stream()
                .collect(Collectors.toMap(
                        c -> c.getEmployee().getEmpId(),
                        Function.identity(),
                        (a, b) -> a
                ));
    }


//    연봉계약 상세
    private List<ContractPayItemResDto> buildFixedPayItems(UUID companyId, SalaryContract contract){
        if(contract.getDetails() == null || contract.getDetails().isEmpty()){
            return List.of();
        }

        List<Long> payItemIds = contract.getDetails().stream()                .map(SalaryContractDetail::getPayItemId)
                .collect(Collectors.toList());

        Map<Long, PayItems> payItemMap = payItemsRepository.findByPayItemIdInAndCompany_CompanyId(payItemIds, companyId).stream().collect(Collectors.toMap(PayItems::getPayItemId, Function.identity()));

        return contract.getDetails().stream().map(detail ->{
            PayItems item = payItemMap.get(detail.getPayItemId());
            return ContractPayItemResDto.builder()
                    .payItemId(detail.getPayItemId())
                    .payItemName(item != null ? item.getPayItemName() : "알 수 없는 항목 ")
                    .amount(detail.getAmount())
                    .build();
        })
                .collect(Collectors.toList());
    }


//        보험료계산
        private long calcAmount(long base, BigDecimal rate){
        if (rate == null){
            return  0L;
        }
        return BigDecimal.valueOf(base)
                .multiply(rate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
        }

//        퇴직연금 타입 설정
    private RetirementType mapPensionToRetirement(PensionType pt) {
        if (pt == null) return null;
        return switch (pt) {
            case DB -> RetirementType.DB;
            case DC -> RetirementType.DC;
            case severance -> RetirementType.severance;
            case DB_DC -> null;
        };
    }

        private long calcHalf(long base, BigDecimal rate){
        if (rate == null){
            return 0L;
        }
        return BigDecimal.valueOf(base)
                .multiply(rate)
                .divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP)
                .longValue();
        }

}

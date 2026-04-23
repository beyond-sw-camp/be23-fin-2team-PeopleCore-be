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

    @Autowired
    public EmpSalaryService(EmployeeRepository employeeRepository, EmpAccountsRepository empAccountsRepository, SalaryContractRepository salaryContractRepository, PayItemsRepository payItemsRepository, EmpRetirementAccountRepository empRetirementAccountRepository, InsuranceRatesRepository insuranceRatesRepository, TaxWithholdingService taxWithholdingService, RetirementRepository retirementRepository) {
        this.employeeRepository = employeeRepository;
        this.empAccountsRepository = empAccountsRepository;
        this.salaryContractRepository = salaryContractRepository;
        this.payItemsRepository = payItemsRepository;
        this.empRetirementAccountRepository = empRetirementAccountRepository;
        this.insuranceRatesRepository = insuranceRatesRepository;
        this.taxWithholdingService = taxWithholdingService;
        this.retirementRepository = retirementRepository;
    }

/// 사원 급여 목록
    public Page<EmpSalaryResDto> getEmpSalaryList(UUID companyId, String keyword, Long deptId, EmpType empType, EmpStatus empStatus, Pageable pageable) {

//        1. Employee 페이징 조회
        Page<Employee> employees = employeeRepository.findAllWithFilter(companyId, keyword, deptId, empType, empStatus, null, null, pageable);

        if (employees.isEmpty()) {
            return employees.map(emp -> EmpSalaryResDto.fromEmployee(emp, null, null, null, null));
        }

        List<Long> empIds = employees.getContent().stream().map(Employee::getEmpId).collect(Collectors.toList());

//        2. SalaryContract 배치 조회(사원별로 최신 계약만)
        Map<Long, SalaryContract> contractMap = buildLastestContrackMap(companyId, empIds);

//        3. EmpAccounts 배치 조회
        Map<Long, EmpAccounts> accountsMap = empAccountsRepository.findByEmployee_EmpIdInAndCompany_CompanyId(empIds, companyId)
                .stream().collect(Collectors.toMap(
                        a -> a.getEmployee().getEmpId(),
                        Function.identity(),
                        (a, b) -> a //중복시 첫번째
                ));

//        4. DTO 조립
        return employees.map(employee -> {
            SalaryContract contract = contractMap.get(employee.getEmpId());
            BigDecimal annual = contract != null ? contract.getTotalAmount() : null;
            Long monthly = annual != null ? annual.divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP).longValue() : null;

            EmpAccounts accounts = accountsMap.get(employee.getEmpId());

            return EmpSalaryResDto.fromEmployee(
                    employee, annual, monthly, accounts != null ? accounts.getBankName() : null, accounts != null ? accounts.getAccountNumber() : null);
        });
    }


///    급여 상세
    public EmpSalaryDetailResDto getEmpSalaryDetail(UUID companyId, Long empId) {
        Employee employee = employeeRepository.findById(empId).orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

//        최신 연봉계약
        List<SalaryContract> contract = salaryContractRepository.findByCompanyIdAndEmployee_EmpIdAndDeletedAtIsNullOrderByContractYearDesc(companyId, empId);

        BigDecimal annualSalary = null;
        Long monthlySalary = null;
        List<ContractPayItemResDto> fixedPayItems = List.of();

        if (!contract.isEmpty()) {
            SalaryContract latestContract = contract.get(0);
            annualSalary = latestContract.getTotalAmount();
            monthlySalary = annualSalary.divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP).longValue();

            fixedPayItems = buildFixedPayItems(companyId, latestContract);
        }

//        급여계좌
        Optional<EmpAccounts> empAccount = empAccountsRepository.findByEmployee_EmpIdAndCompany_CompanyId(empId, companyId);

//        퇴직연금 계좌
        Optional<EmpRetirementAccount> retAccount = empRetirementAccountRepository.findByEmployee_EmpIdAndCompany_CompanyId(empId, companyId);

        return EmpSalaryDetailResDto.builder()
                .empAccountId(employee.getEmpId())
                .empName(employee.getEmpName())
                .empNum(employee.getEmpNum())
                .empEmail(employee.getEmpEmail())
                .empType(employee.getEmpType().name())
                .empStatus(employee.getEmpStatus().name())
                .deptName(employee.getDept().getDeptName())
                .gradeName(employee.getGrade().getGradeName())
                .titleName(employee.getTitle() != null ? employee.getTitle().getTitleName() : null)
                .empHireDate(employee.getEmpHireDate())
                .annualSalary(annualSalary)
                .monthlySalary(monthlySalary)
                .fixedPayItems(fixedPayItems)
                .empAccountId(empAccount.map(EmpAccounts::getEmpAccountId).orElse(null))
                .bankName(empAccount.map(EmpAccounts::getBankName).orElse(null))
                .accountNumber(empAccount.map(EmpAccounts::getAccountNumber).orElse(null))
                .accountHolder(empAccount.map(EmpAccounts::getAccountHolder).orElse(null))
                .retirementAccountId(retAccount.map(EmpRetirementAccount::getRetirementAccountId).orElse(null))
                .empRetirementType(retAccount.map(EmpRetirementAccount::getRetirementType).orElse(null))
                .pensionProvider(retAccount.map(EmpRetirementAccount::getPensionProvider).orElse(null))
                .retirementAccountNumber(retAccount.map(EmpRetirementAccount::getAccountNumber).orElse(null))
                .build();
    }

//    급여계좌 변경
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
    }



///     월급여 예상지급공제
    public ExpectedDeductionSummaryResDto getExpectedDeductions(UUID companyId) {
        List<Employee> employees = employeeRepository.findByCompany_CompanyIdAndEmpStatusInAndDeleteAtIsNull(companyId, List.of(EmpStatus.ACTIVE, EmpStatus.ON_LEAVE));

        if (employees.isEmpty()) {
            return ExpectedDeductionSummaryResDto.builder()
                    .totalEmployees(0)
                    .totalExpectedNetPay(0L)
                    .employees(List.of())
                    .build();
        }

//        현재 연도 보험요율
        int currentYear = LocalDate.now().getYear();
        InsuranceRates rates = insuranceRatesRepository.findByCompany_CompanyIdAndYear(companyId, currentYear).orElse(null);

//        사원별 최신 연봉계약
        List<Long> empIds = employees.stream().map(Employee::getEmpId).collect(Collectors.toList());
        Map<Long, SalaryContract> contractMap = buildLastestContrackMap(companyId, empIds);

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

//                소득세(간이세액표 조회)
                try{
                    TaxWithholdingResDto tax = taxWithholdingService.getTax(currentYear, monthlySalary, emp.getDependentsCount());
                    incomeTax = tax.getIncomeTax();
                    localIncomeTax = tax.getLocalIncomeTax();
                } catch (Exception e){
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
            return ExpectedDeductionSummaryResDto.builder()
                    .totalEmployees(employees.size())
                    .totalExpectedNetPay(grandTotalNet)
                    .employees(result)
                    .build();
    }




//사원별 최신 연봉계약
    private Map<Long, SalaryContract> buildLastestContrackMap(UUID companyId, List<Long> empIds){
        Map<Long, SalaryContract> map = new HashMap<>();
        for (Long empId : empIds){
            List<SalaryContract> contracts = salaryContractRepository.findByCompanyIdAndEmployee_EmpIdAndDeletedAtIsNullOrderByContractYearDesc(companyId, empId);  //첫번째가 최신
            if (!contracts.isEmpty()){
                map.put(empId, contracts.get(0));
            }
        }
        return map;
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

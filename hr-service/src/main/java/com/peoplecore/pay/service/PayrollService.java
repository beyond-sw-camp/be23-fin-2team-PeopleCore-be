package com.peoplecore.pay.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.*;
import com.peoplecore.pay.dtos.*;
import com.peoplecore.pay.enums.PayItemType;
import com.peoplecore.pay.enums.PayrollStatus;
import com.peoplecore.pay.repository.*;
import com.peoplecore.pay.transfer.BankTransferFileFactory;
import com.peoplecore.pay.transfer.BankTransferFileGenerator;
import com.peoplecore.salarycontract.domain.ContractStatus;
import com.peoplecore.salarycontract.domain.SalaryContract;
import com.peoplecore.salarycontract.domain.SalaryContractDetail;
import com.peoplecore.salarycontract.repository.SalaryContractDetailRepository;
import com.peoplecore.salarycontract.repository.SalaryContractRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PayrollService {

    private final PayrollRunsRepository payrollRunsRepository;
    private final PayrollDetailsRepository payrollDetailsRepository;
    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;
    private final SalaryContractRepository salaryContractRepository;
    private final SalaryContractDetailRepository salaryContractDetailRepository;
    private final PayItemsRepository payItemsRepository;
    private final PaySettingsRepository paySettingsRepository;
    private final BankTransferFileFactory bankTransferFileFactory;
    private final EmpAccountsRepository empAccountsRepository;


    @Autowired
    public PayrollService(PayrollRunsRepository payrollRunsRepository, PayrollDetailsRepository payrollDetailsRepository, EmployeeRepository employeeRepository, CompanyRepository companyRepository, SalaryContractRepository salaryContractRepository, SalaryContractDetailRepository salaryContractDetailRepository, PayItemsRepository payItemsRepository, PaySettingsRepository paySettingsRepository, BankTransferFileFactory bankTransferFileFactory, EmpAccountsRepository empAccountsRepository) {
        this.payrollRunsRepository = payrollRunsRepository;
        this.payrollDetailsRepository = payrollDetailsRepository;
        this.employeeRepository = employeeRepository;
        this.companyRepository = companyRepository;
        this.salaryContractRepository = salaryContractRepository;
        this.salaryContractDetailRepository = salaryContractDetailRepository;
        this.payItemsRepository = payItemsRepository;
        this.paySettingsRepository = paySettingsRepository;
        this.bankTransferFileFactory = bankTransferFileFactory;
        this.empAccountsRepository = empAccountsRepository;
    }

///       급여대장 조회(특정 월)
    public PayrollRunResDto getPayroll(UUID companyId, String payYearMonth){

        PayrollRuns run = payrollRunsRepository.findByCompany_CompanyIdAndPayYearMonth(companyId, payYearMonth).orElseThrow(()-> new CustomException(ErrorCode.PAYROLL_NOT_FOUND));

        List<PayrollDetails> allDetails = payrollDetailsRepository.findByPayrollRuns(run);

//        사원별 그룹핑
        Map<Long, List<PayrollDetails>> detailsByEmp = allDetails.stream().collect(Collectors.groupingBy(d -> d.getEmployee().getEmpId()));

        List<PayrollEmpResDto> empList = detailsByEmp.entrySet().stream().map(entry -> {
            Employee emp = entry.getValue().get(0).getEmployee();
            List<PayrollDetails> details = entry.getValue();

            long pay = details.stream().filter(d-> d.getPayItemType() == PayItemType.PAYMENT)
                    .mapToLong(PayrollDetails::getAmount).sum();
            long deduction = details.stream()
                    .filter(d -> d.getPayItemType() == PayItemType.DEDUCTION)
                    .mapToLong(PayrollDetails::getAmount).sum();

            return PayrollEmpResDto.builder()
                    .empId(emp.getEmpId())
                    .empName(emp.getEmpName())
                    .deptName(emp.getDept().getDeptName())
                    .gradeName(emp.getGrade().getGradeName())
                    .empType(emp.getEmpType().name())
                    .status(run.getPayrollStatus().name())
                    .totalPay(pay)
                    .totalDeduction(deduction)
                    .netPay(pay-deduction)
                    .unpaid(run.getPayrollStatus() == PayrollStatus.PAID ? 0L : pay - deduction)
                    .build();
        })
                .toList();

        return PayrollRunResDto.fromEntity(run, empList);
    }

///    급여 산정 생성 (연봉계약 기반)
    @Transactional
    public PayrollRunResDto createPayroll(UUID companyId, String payYearMonth) {

//    중복체크
        if (payrollRunsRepository.existsByCompany_CompanyIdAndPayYearMonth(companyId, payYearMonth)) {
            throw new CustomException(ErrorCode.PAYROLL_ALREADY_EXISTS);
        }

        Company company = companyRepository.findById(companyId).orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

//        재직 + 휴직 사원 목록 (퇴직 제외)
        List<Employee> employees = employeeRepository.findAllWithFilter(companyId, null, null, null, null, null, Pageable.unpaged()).getContent()
                .stream()
                .filter(e -> e.getEmpStatus() != EmpStatus.RESIGNED)
                .filter(e -> e.getCompany().getCompanyId().equals(companyId))
                .toList();

//        payrollRuns 생성
        PayrollRuns run = PayrollRuns.builder()
                .payYearMonth(payYearMonth)
                .payrollStatus(PayrollStatus.CALCULATING)
                .company(company)
                .totalEmployees(employees.size())
                .totalPay(0L)
                .totalDeduction(0L)
                .totalNetPay(0L)
                .build();
        payrollRunsRepository.save(run);

        long totalPay = 0L;
        long totalDeduction = 0L;

        for (Employee emp : employees) {
//            최신 연봉계약 조회
            SalaryContract contract = salaryContractRepository.findTopByEmployee_EmpIdAndStatusOrderByContractYearDesc(emp.getEmpId(), ContractStatus.SIGNED).orElse(null);

            if (contract == null) continue;

//            계약 상세 항목 목록
            List<SalaryContractDetail> contractDetails = salaryContractDetailRepository.findByContractId(contract.getContractId());

//            스냅샷 payItemId -> payItems 매핑
            List<Long> payItemIds = contractDetails.stream()
                    .map(SalaryContractDetail::getPayItemId)
                    .toList();
            Map<Long, PayItems> payItemMap = payItemsRepository.findByPayItemIdInAndCompany_CompanyId(payItemIds, companyId).stream().collect(Collectors.toMap(PayItems::getPayItemId, Function.identity()));

            for (SalaryContractDetail detail : contractDetails) {
                PayItems payItem = payItemMap.get(detail.getPayItemId());
                if (payItem == null) continue;

                PayrollDetails payrollDetail = PayrollDetails.builder()
                        .payrollRuns(run)
                        .employee(emp)
                        .payItems(payItem)
                        .payItemName(payItem.getPayItemName())
                        .payItemType(payItem.getPayItemType())
                        .amount(detail.getAmount().longValue())
                        .company(company)
                        .build();
                payrollDetailsRepository.save(payrollDetail);

                if (payItem.getPayItemType() == PayItemType.PAYMENT) {
                    totalPay += detail.getAmount();
                } else {
                    totalDeduction += detail.getAmount();
                }
            }
        }
//        합계 갱신
        run.updateTotals(employees.size(), totalPay, totalDeduction, totalPay - totalDeduction);

        return getPayroll(companyId, payYearMonth);
    }

///        전월복사
        @Transactional
        public PayrollRunResDto copyFromPreviousMonth(UUID companyId, String payYearMonth){

//            중복체크
            if(payrollRunsRepository.existsByCompany_CompanyIdAndPayYearMonth(companyId, payYearMonth)){
                throw new CustomException(ErrorCode.PAYROLL_ALREADY_EXISTS);
            }

//            전월 계산
            YearMonth current = YearMonth.parse(payYearMonth, DateTimeFormatter.ofPattern("yyyy-MM"));
            String prevMonth = current.minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));

            PayrollRuns prevRun = payrollRunsRepository.findByCompany_CompanyIdAndPayYearMonth(companyId, prevMonth).orElseThrow(()-> new CustomException(ErrorCode.PAYROLL_PREV_NOT_FOUND));

            Company company = prevRun.getCompany();

//            신규 PayrollRuns 생성
            PayrollRuns newRun = PayrollRuns.builder()
                    .payYearMonth(payYearMonth)
                    .payrollStatus(PayrollStatus.CALCULATING)
                    .company(company)
                    .totalEmployees(prevRun.getTotalEmployees())
                    .totalPay(prevRun.getTotalPay())
                    .totalDeduction(prevRun.getTotalDeduction())
                    .totalNetPay(prevRun.getTotalNetPay())
                    .build();
            payrollRunsRepository.save(newRun);

//            전월 상세 복사
            List<PayrollDetails> prevDetails = payrollDetailsRepository.findByPayrollRuns(prevRun);

            for (PayrollDetails prev : prevDetails){
//                퇴직자 제외
                if (prev.getEmployee().getEmpStatus() == EmpStatus.RESIGNED) continue;

                PayrollDetails copy = PayrollDetails.builder()
                        .payrollRuns(newRun)
                        .employee(prev.getEmployee())
                        .payItems(prev.getPayItems())
                        .payItemName(prev.getPayItemName())
                        .payItemType(prev.getPayItemType())
                        .amount(prev.getAmount())
                        .company(company)
                        .build();

                payrollDetailsRepository.save(copy);
            }

            return getPayroll(companyId, payYearMonth);
        }


///        사원별 급여 상세 조회
    public PayrollEmpDetailResDto getEmpPayrollDetail(UUID companyId, Long payrollRunId, Long empId){

        PayrollRuns run = findPayrollRun(companyId, payrollRunId);

        List<PayrollDetails> details = payrollDetailsRepository.findByPayrollRunsAndEmployee_EmpId(run, empId);

        if (details.isEmpty()){
            throw new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND);
        }

        Employee emp = details.get(0).getEmployee();

        List<PayrollEmpDetailResDto.PayrollItemDto> paymentItems = details.stream()
                .filter(d-> d.getPayItemType() == PayItemType.PAYMENT)
                .map(d -> PayrollEmpDetailResDto.PayrollItemDto.builder()
                        .payItemId(d.getPayItems().getPayItemId())
                        .payItemName(d.getPayItemName())
                        .amount(d.getAmount())
                        .build())
                .toList();

        List<PayrollEmpDetailResDto.PayrollItemDto> deductionItems = details.stream()
                .filter(d-> d.getPayItemType() == PayItemType.DEDUCTION)
                .map(d-> PayrollEmpDetailResDto.PayrollItemDto.builder()
                        .payItemId(d.getPayItems().getPayItemId())
                        .amount(d.getAmount())
                        .build())
                .toList();

        long totalPay = paymentItems.stream().mapToLong(PayrollEmpDetailResDto.PayrollItemDto::getAmount).sum();
        long totalDeduction = deductionItems.stream().mapToLong(PayrollEmpDetailResDto.PayrollItemDto::getAmount).sum();

        return PayrollEmpDetailResDto.builder()
                .empID(emp.getEmpId())
                .empName(emp.getEmpName())
                .deptName(emp.getDept().getDeptName())
                .gradeName(emp.getGrade().getGradeName())
                .empType(emp.getEmpType().name())
                .paymentItems(paymentItems)
                .deductionItems(deductionItems)
                .netPay(totalPay - totalDeduction)
                .build();
    }


///    급여 확정
    @Transactional
    public void confirmPayroll(UUID companyId, Long payrollRunId){
        PayrollRuns run = findPayrollRun(companyId, payrollRunId);
        run.confirm();
    }


///    전자결재 상신(급여지급결의서) - 프론트에서 approvalDocId를 받음
    @Transactional
    public void submitApproval(UUID companyId, Long payrollRunId, Long approvalDovId) {
        PayrollRuns run = findPayrollRun(companyId, payrollRunId);
        run.submitApproval(approvalDovId);
    }


///    전자결재 승인완료 처리(kafka consumer)
    @Transactional
    public void approvePayroll(UUID companyId, Long payrollRunId){
        PayrollRuns run = findPayrollRun(companyId, payrollRunId);
        run.approve();
    }


///     지급 처리
    @Transactional
    public void processPayment(UUID companyId, Long payrollRunId){
        PayrollRuns run = findPayrollRun(companyId, payrollRunId);
        run.markPaid(LocalDate.now());
    }


///    대량이체 파일생성
    public TransferFileResDto generateTransferFile(UUID companyId, Long payrollRunId) {
        PayrollRuns run = findPayrollRun(companyId, payrollRunId);

        if (run.getPayrollStatus() != PayrollStatus.PAID){
            throw new CustomException(ErrorCode.PAYROLL_STATUS_INVALID);
        }

//        회사 주거래은행 조회
        CompanyPaySettings settings = paySettingsRepository.findByCompany_CompanyId(companyId).orElseThrow(()-> new CustomException(ErrorCode.PAY_SETTINGS_NOT_FOUND));

        String mainBankCode = settings.getMainBankCode();

//        사원별 실지급액 + 계좌정보 조회
        List<PayrollDetails> details = payrollDetailsRepository.findByPayrollRuns_PayrollRunsId(payrollRunId);

//        사원별 실지급액 집계 (지급합계 - 공제합계)
        Map<Long, Long> empNetPayMap = details.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getEmployee().getEmpId(),
                        Collectors.summingLong(d-> d.getPayItemType() == PayItemType.PAYMENT ? d.getAmount() : -d.getAmount())
                ));

//        사원 계좌정보 배치 조회
        List<Long> empIds = new ArrayList<>(empNetPayMap.keySet());
        Map<Long, EmpAccounts> empAccountMap = empAccountsRepository.findByEmployee_EmpIdInAndCompany_CompanyId(empIds, companyId)
                .stream()
                .collect(Collectors.toMap(a-> a.getEmployee().getEmpId(), a-> a));

//        이체데이터 구성
        List<PayrollTransferDto> transfer = empNetPayMap.entrySet().stream()
                .map(entry -> {
                    if(entry.getValue() <= 0) return null;
                    EmpAccounts account = empAccountMap.get(entry.getKey());
                return PayrollTransferDto.builder()
                        .empName(account.getAccountHolder())
                        .bankCode(account.getBankCode())
                        .bankName(account.getBankName())
                        .accountNumber(account.getAccountNumber().replace("-", ""))
                        .netPay(entry.getValue())
                        .memo(run.getPayYearMonth() +" 급여")
                        .build();
                })
                .filter(Objects::nonNull)
                .toList();

//        은행별 파일 생성
        BankTransferFileGenerator generator = bankTransferFileFactory.getGenerator(mainBankCode);
        byte[] fileBytes = generator.generate(transfer);
        String fileName = generator.getFileName(run.getPayYearMonth());

        return TransferFileResDto.builder()
                .fileName(fileName)
                .fileBytes(fileBytes)
                .build();
    }





    private PayrollRuns findPayrollRun(UUID companyId, Long payrollRunId){
        return payrollRunsRepository.findByPayrollRunIdAndCompany_CompanyId(payrollRunId, companyId).orElseThrow(()-> new CustomException(ErrorCode.PAYROLL_NOT_FOUND));
    }

}

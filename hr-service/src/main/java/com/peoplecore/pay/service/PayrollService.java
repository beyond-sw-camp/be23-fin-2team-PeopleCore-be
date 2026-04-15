package com.peoplecore.pay.service;

import com.peoplecore.attendance.entity.CommuteRecord;
import com.peoplecore.attendance.entity.WorkGroup;
import com.peoplecore.attendance.repository.CommuteRecordRepository;
import com.peoplecore.attendance.repository.WorkGroupRepository;
import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.event.PayrollApprovalResultEvent;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.*;
import com.peoplecore.pay.dtos.*;
import com.peoplecore.pay.enums.LegalCalcType;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static reactor.netty.http.HttpConnectionLiveness.log;

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
    private final CommuteRecordRepository commuteRecordRepository;
    private final InsuranceRatesRepository insuranceRatesRepository;
    private final TaxWithholdingService taxWithholdingService;


    @Autowired
    public PayrollService(PayrollRunsRepository payrollRunsRepository, PayrollDetailsRepository payrollDetailsRepository, EmployeeRepository employeeRepository, CompanyRepository companyRepository, SalaryContractRepository salaryContractRepository, SalaryContractDetailRepository salaryContractDetailRepository, PayItemsRepository payItemsRepository, PaySettingsRepository paySettingsRepository, BankTransferFileFactory bankTransferFileFactory, EmpAccountsRepository empAccountsRepository, CommuteRecordRepository commuteRecordRepository, InsuranceRatesRepository insuranceRatesRepository, TaxWithholdingService taxWithholdingService) {
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
        this.commuteRecordRepository = commuteRecordRepository;
        this.insuranceRatesRepository = insuranceRatesRepository;
        this.taxWithholdingService = taxWithholdingService;
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
            List<SalaryContractDetail> contractDetails = salaryContractDetailRepository.findByContract_ContractId(contract.getContractId());

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


///    전자결재 결과 처리(kafka consumer)
    @Transactional
    public void applyApprovalResult(PayrollApprovalResultEvent event){
        PayrollRuns run = findPayrollRun(event.getCompanyId(), event.getPayrollRunId());

//        approvalDocId 보완
        if(run.getApprovalDocId() == null && event.getApprovalDocId() != null){
            run.bindApprovalDoc(event.getApprovalDocId());
        }

        String status = event.getStatus();
        if ("APPROVED".equals(status)){
            run.approve();
            log.info("[PayrollService] 전자결재 승인 처리 완료 - payrollRunId={}",
                    event.getPayrollRunId());
        } else if ("REJECTED".equals(status)){
            run.rejectApproval();
            log.info("[PayrollService] 전자결재 반려 처리 - payrollRunId={}, reason={}",
                    event.getPayrollRunId(), event.getRejectReason());
        }
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
        List<PayrollDetails> details = payrollDetailsRepository.findByPayrollRuns_PayrollRunId(payrollRunId);

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


///    일당/시급 기준 조회
    public WageInfoResDto getWageInfo(UUID companyId, Long payrollRunId, Long empId){

        findPayrollRun(companyId, payrollRunId);

//        최신 연봉계약의 통상임금
        SalaryContract contract = salaryContractRepository
                .findTopByEmployee_EmpIdAndStatusOrderByContractYearDesc(empId, ContractStatus.SIGNED).orElseThrow(()-> new CustomException(ErrorCode.SALARY_CONTRACT_NOT_FOUND));

        long monthlySalary = contract.getTotalAmount().divide(BigDecimal.valueOf(12), 0, RoundingMode.FLOOR).longValue();

//        시급 = 통상임금(월) % 209
        long hourlyWage = Math.round((double) monthlySalary / 209);
//        일당 = 시급 * 일근무시간(사원별 근무그룹)

        Employee emp = employeeRepository.findById(empId).orElseThrow(()-> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
        WorkGroup empWorkHour = emp.getWorkGroup();
        Duration workTime = Duration.between(empWorkHour.getGroupStartTime(), empWorkHour.getGroupEndTime()).minus(Duration.between(empWorkHour.getGroupBreakStart(), empWorkHour.getGroupBreakEnd()));

        long dailyWorkMinutes = workTime.toMinutes();   //분 단위

        long dailyWage = hourlyWage * dailyWorkMinutes;
        return WageInfoResDto.builder()
                .hourlyWage(hourlyWage)
                .dailyWage(dailyWage)
                .build();
    }

///    이달 승인된 초과근무 조회(CommuteRecord 기반)
    public ApprovedOvertimeResDto getApprovedOvertime(UUID companyId, Long payrollRunId, Long empId){

        PayrollRuns run = findPayrollRun(companyId, payrollRunId);

//        해당 월 범위 계산
        YearMonth ym = YearMonth.parse(run.getPayYearMonth(), DateTimeFormatter.ofPattern("yyyy-MM"));
        LocalDate startDate = ym.atDay(1); //해당월의 1일
        LocalDate endDate = ym.atEndOfMonth();       //해당월의  마지막날짜

//        CommuteRecord에서 인정된 초과근무 일별 기록 조회
        List<CommuteRecord> records = commuteRecordRepository.findRecognizedByMonth(empId, startDate, endDate);

//        월간 합계 집계
        long totalExtMin = 0L, totalNightMin = 0L, totalHolidayMin = 0L;
        List<ApprovedOvertimeResDto.DailyOvertimeDto> dailyItems = new ArrayList<>();

        for(CommuteRecord cr : records){
            totalExtMin += cr.getRecognizedExtendedMinutes();
            totalNightMin += cr.getRecognizedNightMinutes();
            totalHolidayMin += cr.getRecognizedHolidayMinutes();

            dailyItems.add(ApprovedOvertimeResDto.DailyOvertimeDto.builder()
                    .workDate(cr.getWorkDate())
                    .recognizedExtendedMinutes(cr.getRecognizedExtendedMinutes())
                    .recognizedNightMinutes(cr.getRecognizedNightMinutes())
                    .recognizedExtendedMinutes(cr.getRecognizedHolidayMinutes())
                    .actualWorkMinutes(cr.getActualWorkMinutes())
                    .build());
        }


//        시급조회 + 수당 계산
        WageInfoResDto wageInfo = getWageInfo(companyId, payrollRunId, empId);
        long hourlyWage = wageInfo.getHourlyWage();

        long extendedPay = Math.round(hourlyWage * 0.5 * totalExtMin / 60.0); //분 -> 시로 변환
        long nightPay = Math.round(hourlyWage * 0.5 * totalNightMin / 60.0);
        long holidayPay = Math.round(hourlyWage * 0.5 * totalHolidayMin / 60.0);
        long totalAmount = extendedPay + nightPay + holidayPay;

//        이미 적용 여부 확인(해당 사원의 초과근무 수당 PayrollDetails 존재 여부)
        boolean applied = payrollDetailsRepository.existsByPayrollRunsAndEmployee_EmpIdAndIsOvertimePayTrue(run, empId);

        return ApprovedOvertimeResDto.builder()
                .totalExtendedMinutes(totalExtMin)
                .totalNightMinutes(totalNightMin)
                .totalHolidayMinutes(totalHolidayMin)
                .extendedPay(extendedPay)
                .nightPay(nightPay)
                .holidayPay(holidayPay)
                .totalAmount(totalAmount)
                .applied(applied)
                .dailyItems(dailyItems)
                .build();
    }


///    초과근무 수당 적용 (CommuteRecord 월간 집계 + PayrollDetails)
    @Transactional
    public void applyOverTime(UUID companyId, Long payrollRunId, Long empId) {
        PayrollRuns run = findPayrollRun(companyId, payrollRunId);

        if (run.getPayrollStatus() != PayrollStatus.CALCULATING) {
            throw new CustomException(ErrorCode.PAYROLL_STATUS_INVALID);
        }

//      중복체크 - 이미 적용된 초과근무 수당이 있으면 예외
        if (payrollDetailsRepository.existsByPayrollRunsAndEmployee_EmpIdAndIsOvertimePayTrue(run, empId)) {
            throw new CustomException(ErrorCode.OVERTIME_ALREADY_APPLIED);
        }

//        월간 승인 초과근무 조회
        ApprovedOvertimeResDto overtime = getApprovedOvertime(companyId, payrollRunId, empId);

        if (overtime.getTotalExtendedMinutes() == 0
                && overtime.getTotalNightMinutes() == 0
                && overtime.getTotalHolidayMinutes() == 0) {
            return; //인정된 초과근무 없음
        }

        Employee emp = employeeRepository.findById(empId).orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

//        유형별 수당 -> 0보다 큰것만 생성
        Map<LegalCalcType, Long> payMap = new LinkedHashMap<>();
        if (overtime.getExtendedPay() > 0) {
            payMap.put(LegalCalcType.OVERTIME, overtime.getExtendedPay());
        }
        if (overtime.getNightPay() > 0) {
            payMap.put(LegalCalcType.NIGHT, overtime.getExtendedPay());
        }
        if (overtime.getHolidayPay() > 0) {
            payMap.put(LegalCalcType.HOLIDAY, overtime.getHolidayPay());
        }

        for (Map.Entry<LegalCalcType, Long> entry : payMap.entrySet()) {
            PayItems payItems = payItemsRepository.findByCompany_CompanyIdAndIsLegalTrueAndLegalCalcType(companyId, entry.getKey()).orElseThrow(() -> new CustomException(ErrorCode.PAY_ITEM_NOT_FOUND));

            PayrollDetails detail = PayrollDetails.builder()
                    .payrollRuns(run)
                    .employee(emp)
                    .payItems(payItems)
                    .payItemName(payItems.getPayItemName())
                    .payItemType(PayItemType.PAYMENT)
                    .amount(entry.getValue())
                    .isOvertimePay(true)
                    .company(run.getCompany())
                    .build();

            payrollDetailsRepository.save(detail);
        }
//        합계 갱신
        recalculateTotals(run);
    }


//    지급합계 기반 공제항목 실시간 계산
    public CalcDeductionResDto calcDeductions(UUID companyId, CalcDeductionReqDto reqDto){

    long totalPay = reqDto.getTotalPay();

//    해당연도 보험요율 조회
        int currentYear = LocalDate.now().getYear();
        InsuranceRates rates = insuranceRatesRepository.findByCompany_CompanyIdAndYear(companyId, currentYear).orElseThrow(()-> new CustomException(ErrorCode.INSURANCE_RATES_NOT_FOUND));

//        4대보험 근로자 부담분 계산
        long pensionBase = totalPay;
//        국민연금 = 보수월액 * 요율 /2 , 상/하한 적용
        if (pensionBase > rates.getPensionUpperLimit()) pensionBase = rates.getPensionUpperLimit();
        if (pensionBase < rates.getPensionLowerLimit()) pensionBase = rates.getPensionLowerLimit();
        long pension = Math.round(pensionBase * rates.getNationalPension().doubleValue() / 2);
//        건강보험 = 보수월액 * 요율 /2
        long health = Math.round(totalPay * rates.getHealthInsurance().doubleValue() / 2);
//        장기요양보험 = 건강보험(전액) * 요율 /2
        long healthTotal = Math.round(totalPay * rates.getHealthInsurance().doubleValue() /2);
        long ltc = Math.round(healthTotal * rates.getLongTermCare().doubleValue() /2);
//        고용보험 = 보수월액 * 근로자요율
        long employment = Math.round(totalPay * rates.getEmploymentInsurance().doubleValue());

//        소득세
        Employee emp = employeeRepository.findById(reqDto.getEmpId()).orElseThrow(()-> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
        TaxWithholdingResDto tax = taxWithholdingService.getTax(currentYear, totalPay, emp.getDependentsCount());

        long incomeTax = tax.getIncomeTax();
        long localIncomeTax = tax.getLocalIncomeTax();

//        합계
        long totalDeduction = pension + health + ltc + employment + incomeTax + localIncomeTax;

        return CalcDeductionResDto.builder()
                .nationalPension(pension)
                .healthInsurance(health)
                .longTermCare(ltc)
                .employmentInsurance(employment)
                .incomeTax(incomeTax)
                .localIncomeTax(localIncomeTax)
                .totalDeduction(totalDeduction)
                .netPay(totalPay - totalDeduction)
                .build();
        }


//    합계 재계산
    private void recalculateTotals(PayrollRuns run){
        List<PayrollDetails> allDetails = payrollDetailsRepository.findByPayrollRuns(run);

        long totalPay = allDetails.stream()
                .filter(d-> d.getPayItemType() == PayItemType.PAYMENT)
                .mapToLong(PayrollDetails::getAmount).sum();
        long totalDeduction = allDetails.stream()
                .filter(d-> d.getPayItemType() == PayItemType.DEDUCTION)
                .mapToLong(PayrollDetails::getAmount).sum();

        int empCount = (int) allDetails.stream()
                .map(d-> d.getEmployee().getEmpId())
                .distinct().count();

        run.updateTotals(empCount, totalPay, totalDeduction,totalPay - totalDeduction);
    }





    private PayrollRuns findPayrollRun(UUID companyId, Long payrollRunId){
        return payrollRunsRepository.findByPayrollRunIdAndCompany_CompanyId(payrollRunId, companyId).orElseThrow(()-> new CustomException(ErrorCode.PAYROLL_NOT_FOUND));
    }

}

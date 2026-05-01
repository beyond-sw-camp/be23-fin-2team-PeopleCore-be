package com.peoplecore.pay.service;

import com.peoplecore.attendance.entity.CommuteRecord;
import com.peoplecore.attendance.entity.OvertimeRequest;
import com.peoplecore.attendance.entity.WorkGroup;
import com.peoplecore.attendance.repository.CommuteRecordRepository;
import com.peoplecore.attendance.repository.OvertimeRequestRepository;
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
import com.peoplecore.pay.enums.*;
import com.peoplecore.pay.repository.*;
import com.peoplecore.pay.transfer.BankTransferFileFactory;
import com.peoplecore.pay.transfer.BankTransferFileGenerator;
import com.peoplecore.salarycontract.domain.SalaryContract;
import com.peoplecore.salarycontract.domain.SalaryContractDetail;
import com.peoplecore.salarycontract.repository.SalaryContractDetailRepository;
import com.peoplecore.salarycontract.repository.SalaryContractRepository;
import com.peoplecore.vacation.service.BusinessDayCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
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
    private final OvertimeRequestRepository overtimeRequestRepository;
    private final BusinessDayCalculator businessDayCalculator;
    private final InsuranceRatesRepository insuranceRatesRepository;
    private final TaxWithholdingService taxWithholdingService;
    private final RetirementPensionDepositsRepository depositRepository;
    private final MySalaryCacheService mySalaryCacheService;
    private final PayrollEmpStatusRepository payrollEmpStatusRepository;


    @Autowired
    public PayrollService(PayrollRunsRepository payrollRunsRepository, PayrollDetailsRepository payrollDetailsRepository, EmployeeRepository employeeRepository, CompanyRepository companyRepository, SalaryContractRepository salaryContractRepository, SalaryContractDetailRepository salaryContractDetailRepository, PayItemsRepository payItemsRepository, PaySettingsRepository paySettingsRepository, BankTransferFileFactory bankTransferFileFactory, EmpAccountsRepository empAccountsRepository, CommuteRecordRepository commuteRecordRepository, OvertimeRequestRepository overtimeRequestRepository, BusinessDayCalculator businessDayCalculator, InsuranceRatesRepository insuranceRatesRepository, TaxWithholdingService taxWithholdingService, RetirementPensionDepositsRepository depositRepository, MySalaryCacheService mySalaryCacheService, PayrollEmpStatusRepository payrollEmpStatusRepository) {
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
        this.overtimeRequestRepository = overtimeRequestRepository;
        this.businessDayCalculator = businessDayCalculator;
        this.insuranceRatesRepository = insuranceRatesRepository;
        this.taxWithholdingService = taxWithholdingService;
        this.depositRepository = depositRepository;
        this.mySalaryCacheService = mySalaryCacheService;
        this.payrollEmpStatusRepository = payrollEmpStatusRepository;
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

            // 사원별 산정 상태 일괄 조회
            Map<Long, String> empStatusMap = payrollEmpStatusRepository
                    .findByPayrollRuns_PayrollRunId(run.getPayrollRunId())
                    .stream()
                    .collect(Collectors.toMap(
                            s -> s.getEmployee().getEmpId(),
                            s -> s.getStatus().name()
                    ));

            return PayrollEmpResDto.builder()
                    .empId(emp.getEmpId())
                    .empName(emp.getEmpName())
                    .deptName(emp.getDept().getDeptName())
                    .gradeName(emp.getGrade().getGradeName())
                    .empType(emp.getEmpType().name())
                    .status(run.getPayrollStatus().name())
                    .payrollEmpStatus(empStatusMap.getOrDefault(emp.getEmpId(), "CALCULATING"))
                    .empStatus(emp.getEmpStatus().name())
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

//        급여대상 사원(재직+휴직) 목록 (퇴직 제외)
        YearMonth payMonth = YearMonth.parse(payYearMonth);
        List<Employee> employees = employeeRepository.findAllForPayroll(companyId, payMonth);
        int year = payMonth.getYear();

        // 보험요율 (해당 연도)
        InsuranceRates rates = insuranceRatesRepository.findByCompany_CompanyIdAndYear(companyId, year).orElse(null);

        // 공제 항목(시스템 마스터) 일괄 조회 → name → PayItems 맵
        List<String> deductionNames = List.of("국민연금", "건강보험", "장기요양보험", "고용보험", "근로소득세", "근로지방소득세", "산재보험");
        Map<String, PayItems> deductionMap = payItemsRepository
                .findByCompany_CompanyIdAndPayItemTypeAndPayItemNameIn(companyId, PayItemType.DEDUCTION, deductionNames)
                .stream()
                .collect(Collectors.toMap(PayItems::getPayItemName, Function.identity()));

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
            SalaryContract contract = salaryContractRepository.findTopByEmployee_EmpIdOrderByApplyFromDesc(emp.getEmpId()).orElse(null);
            if (contract == null) continue;

            // 사원별 산정 상태 (기본 CALCULATING)
            payrollEmpStatusRepository.save(PayrollEmpStatus.builder()
                    .payrollRuns(run)
                    .employee(emp)
                    .status(PayrollEmpStatusType.CALCULATING)
                    .companyId(companyId)
                    .build());

//            1. 계약 상세 항목 (지급항목 위주)
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
                    totalPay += detail.getAmount().longValue();
                } else {
                    totalDeduction += detail.getAmount().longValue();
                }
            }

            // 2. 4대보험 + 세금 자동 계산 (공제항목)
            long monthlySalary = (contract.getTotalAmount() != null)
                    ? contract.getTotalAmount()
                    .divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP)
                    .longValue()
                    : 0L;

            long calcDed = insertCalculatedDeductions(run, emp, company, monthlySalary, year, rates, deductionMap);
            totalDeduction += calcDed;
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
                        .payItemName(d.getPayItemName())
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

///    급여 수정
    @Transactional
    public void updateEmpDetails(UUID companyId, Long payrollRunId, Long empId, PayrollDetailUpdateReqDto reqDto){
        PayrollRuns run = findPayrollRun(companyId, payrollRunId);

        // 결재 단계 이상이면 차단
        if (run.getPayrollStatus() != PayrollStatus.CALCULATING
                && run.getPayrollStatus() != PayrollStatus.CONFIRMED) {
            throw new CustomException(ErrorCode.PAYROLL_STATUS_INVALID);
        }

        // 해당 사원이 이미 CONFIRMED 면 차단
        PayrollEmpStatus empStatus = payrollEmpStatusRepository
                .findByPayrollRuns_PayrollRunIdAndEmployee_EmpId(payrollRunId, empId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
        if (empStatus.getStatus() == PayrollEmpStatusType.CONFIRMED) {
            throw new CustomException(ErrorCode.PAYROLL_EMP_ALREADY_CONFIRMED);
        }

        // 기존 detail 조회 → payItemId 로 매핑
        List<PayrollDetails> details = payrollDetailsRepository.findByPayrollRunsAndEmployee_EmpId(run, empId);
        Map<Long, PayrollDetails> detailMap = details.stream()
                .collect(Collectors.toMap(d -> d.getPayItems().getPayItemId(), Function.identity()));

        // 요청 항목 갱신 (없는 항목은 무시)
        for (PayrollDetailUpdateReqDto.Item item : reqDto.getItems()) {
            PayrollDetails d = detailMap.get(item.getPayItemId());
            if (d == null) continue;
            d.updateAmount(item.getAmount());
        }

        // run 합계 재집계
        List<PayrollDetails> all = payrollDetailsRepository.findByPayrollRuns(run);
        long totalPay = all.stream()
                .filter(d -> d.getPayItemType() == PayItemType.PAYMENT)
                .mapToLong(PayrollDetails::getAmount).sum();
        long totalDeduction = all.stream()
                .filter(d -> d.getPayItemType() == PayItemType.DEDUCTION)
                .mapToLong(PayrollDetails::getAmount).sum();
        run.updateTotals(run.getTotalEmployees(), totalPay, totalDeduction, totalPay - totalDeduction);

    }


///    급여 확정(전체)
    @Transactional
    public void confirmPayroll(UUID companyId, Long actorEmpId, Long payrollRunId){
        PayrollRuns run = findPayrollRun(companyId, payrollRunId);

        // 모든 사원 산정중 → 확정 (이미 확정된 사원은 건드리지 않음)
        List<PayrollEmpStatus> allEmps = payrollEmpStatusRepository
                .findByPayrollRuns_PayrollRunId(payrollRunId);
        for (PayrollEmpStatus pes : allEmps) {
            if (pes.getStatus() == PayrollEmpStatusType.CALCULATING) {
                pes.confirm(actorEmpId);
            }
        }
        run.confirm();
    }

///    급여 확정(사원별)
    @Transactional
    public void confirmEmployee(UUID companyId, Long payrollRunId, Long empId, Long actorEmpId) {
        PayrollEmpStatus pes = payrollEmpStatusRepository
                .findByPayrollRuns_PayrollRunIdAndEmployee_EmpId(payrollRunId, empId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYROLL_EMP_NOT_FOUND));
        pes.confirm(actorEmpId);
    }

///     확정 되돌리기(사원별)
    @Transactional
    public void revertEmployee(UUID companyId, Long payrollRunId, Long empId) {
        PayrollEmpStatus pes = payrollEmpStatusRepository
                .findByPayrollRuns_PayrollRunIdAndEmployee_EmpId(payrollRunId, empId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYROLL_EMP_NOT_FOUND));
        pes.revert();
    }

/// 전자결재 상신은 PayrollApprovalDraftService 로직에서 처리

///    전자결재 결과 처리(kafka consumer)
    @Transactional
    public void applyApprovalResult(PayrollApprovalResultEvent event){
        PayrollRuns run = findPayrollRun(event.getCompanyId(), event.getPayrollRunId());

//        approvalDocId 보완
        if(run.getApprovalDocId() == null && event.getApprovalDocId() != null){
            run.bindApprovalDoc(event.getApprovalDocId());
            run.submitApproval(event.getApprovalDocId());
        }

        String status = event.getStatus();
        if ("APPROVED".equals(status)){
            run.approve();
            log.info("[PayrollService] 전자결재 승인 처리 완료 - payrollRunId={}",
                    event.getPayrollRunId());

            // 승인 시 redis에 저장해둔 캐시 무효화(invalidate)
            List<Long> empIds = payrollDetailsRepository
                    .findDistinctEmpIdsByPayrollRunId(event.getPayrollRunId());
            for (Long empId : empIds) {
                mySalaryCacheService.evictSalaryInfoCache(event.getCompanyId(), empId);
                mySalaryCacheService.evictStubListCache(event.getCompanyId(), empId);
                mySalaryCacheService.evictSeveranceEstimateCache(event.getCompanyId(), empId);
            }
            log.info("[PayrollService] 캐시 invalidate - runId={}, empCount={}",
                    event.getPayrollRunId(), empIds.size());

        } else if ("REJECTED".equals(status)){
            run.rejectApproval();
            log.info("[PayrollService] 전자결재 반려 처리 - payrollRunId={}, reason={}",
                    event.getPayrollRunId(), event.getRejectReason());
        } else if ("CANCELED".equals(status)) {
            run.cancelApproval();
            log.info("[PayrollService] 전자결재 회수 - payrollRunId={}", event.getPayrollRunId());
        } else {
            log.warn("[PayrollService] 알 수 없는 status={} - payrollRunId={}",
                    status, event.getPayrollRunId());
        }
    }


///     지급 처리
    @Transactional
    public void processPayment(UUID companyId, Long payrollRunId){
        PayrollRuns run = findPayrollRun(companyId, payrollRunId);
        run.markPaid(LocalDate.now());

//        DC형 퇴직연금 적립 데이터 저장
        createDcDeposits(run, run.getCompany());
    }


///    선택 사원 대량이체 파일생성
    public TransferFileResDto generateTransferFile(UUID companyId, Long payrollRunId, List<Long> empIds) {
        PayrollRuns run = findPayrollRun(companyId, payrollRunId);

        if (run.getPayrollStatus() != PayrollStatus.PAID){
            throw new CustomException(ErrorCode.PAYROLL_STATUS_INVALID);
        }

        CompanyPaySettings settings = paySettingsRepository.findByCompany_CompanyId(companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_SETTINGS_NOT_FOUND));

        String mainBankCode = settings.getMainBankCode();

        // 선택된 사원의 급여상세만 조회
        List<PayrollDetails> details = payrollDetailsRepository.findByPayrollRuns_PayrollRunId(payrollRunId)
                .stream()
                .filter(d -> empIds.contains(d.getEmployee().getEmpId()))
                .toList();

        Map<Long, Long> empNetPayMap = details.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getEmployee().getEmpId(),
                        Collectors.summingLong(d -> d.getPayItemType() == PayItemType.PAYMENT ? d.getAmount() : -d.getAmount())
                ));

        Map<Long, EmpAccounts> empAccountMap = empAccountsRepository
                .findByEmployee_EmpIdInAndCompany_CompanyId(new ArrayList<>(empNetPayMap.keySet()), companyId)
                .stream()
                .collect(Collectors.toMap(a -> a.getEmployee().getEmpId(), a -> a));

        List<PayrollTransferDto> transfer = empNetPayMap.entrySet().stream()
                .map(entry -> {
                    if (entry.getValue() <= 0) return null;
                    EmpAccounts account = empAccountMap.get(entry.getKey());
                    return PayrollTransferDto.builder()
                            .empName(account.getAccountHolder())
                            .bankCode(account.getBankCode())
                            .bankName(account.getBankName())
                            .accountNumber(account.getAccountNumber().replace("-", ""))
                            .netPay(entry.getValue())
                            .memo(run.getPayYearMonth() + " 급여")
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();

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
                .findTopByEmployee_EmpIdOrderByApplyFromDesc(empId).orElseThrow(()-> new CustomException(ErrorCode.SALARY_CONTRACT_NOT_FOUND));

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

///    이달 승인된 초과근무 조회(OvertimeRequest 기반)
    public ApprovedOvertimeResDto getApprovedOvertime(UUID companyId, Long payrollRunId, Long empId){

        PayrollRuns run = findPayrollRun(companyId, payrollRunId);

//        해당 월 범위 계산
        YearMonth ym = YearMonth.parse(run.getPayYearMonth(), DateTimeFormatter.ofPattern("yyyy-MM"));
        LocalDateTime monthStart = ym.atDay(1).atStartOfDay(); //해당월의 1일
        LocalDateTime monthEnd = ym.atEndOfMonth().atTime(LocalTime.MAX);       //해당월의 마지막날짜

//        OvertimeRequest에서 승인된 초과근무 신청 (당사자 + APPROVED + 해당월)
        List<OvertimeRequest> approved = overtimeRequestRepository
                .findApprovedByEmpAndDateRange(empId, monthStart, monthEnd);

        Employee emp = employeeRepository.findById(empId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
        WorkGroup wg = emp.getWorkGroup();

//        월간 합계 집계
        long totalExtMin = 0L, totalNightMin = 0L, totalHolidayMin = 0L;
        List<ApprovedOvertimeResDto.DailyOvertimeDto> dailyItems = new ArrayList<>();

        for(OvertimeRequest ot : approved){
            long minutes = Duration.between(ot.getOtPlanStart(), ot.getOtPlanEnd()).toMinutes();
            if (minutes <= 0) continue;

            LocalDate otDate = ot.getOtDate().toLocalDate();
            boolean holiday = isHolidayForWorkGroup(companyId, otDate, wg);
            long nightMin = nightOverlapMinutes(ot.getOtPlanStart(), ot.getOtPlanEnd());

            long extMin = 0L, holMin = 0L;
            if (holiday) holMin = minutes;
            else         extMin = minutes;

            totalExtMin     += extMin;
            totalHolidayMin += holMin;
            totalNightMin   += nightMin;

            dailyItems.add(ApprovedOvertimeResDto.DailyOvertimeDto.builder()
                    .workDate(otDate)
                    .recognizedExtendedMinutes(extMin)
                    .recognizedNightMinutes(nightMin)
                    .recognizedHolidayMinutes(holMin)
                    .actualWorkMinutes(minutes)
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
            payMap.put(LegalCalcType.NIGHT, overtime.getNightPay());
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

        long incomeTax = tax != null ? tax.getIncomeTax() : 0L;
        long localIncomeTax = tax != null ? tax.getLocalIncomeTax() : 0L;

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



    // 4대보험 + 세금 자동 계산 후 PayrollDetails INSERT
    private long insertCalculatedDeductions(PayrollRuns run, Employee emp, Company company, long monthlySalary, int year, InsuranceRates rates, Map<String, PayItems> deductionMap) {
        if (monthlySalary <= 0) return 0L;

        long total = 0L;

        // 4대보험 (요율 데이터 있을 때만)
        if (rates != null) {
            // 국민연금 (상하한 적용)
            long pensionBase = monthlySalary;
            if (rates.getPensionUpperLimit() != null && pensionBase > rates.getPensionUpperLimit()) {
                pensionBase = rates.getPensionUpperLimit();
            }
            if (rates.getPensionLowerLimit() != null && pensionBase < rates.getPensionLowerLimit()) {
                pensionBase = rates.getPensionLowerLimit();
            }
            long pension = calcHalf(pensionBase, rates.getNationalPension());
            total += saveDeductionDetail(run, emp, company, "국민연금", pension, deductionMap);

            // 건강보험
            long health = calcHalf(monthlySalary, rates.getHealthInsurance());
            total += saveDeductionDetail(run, emp, company, "건강보험", health, deductionMap);

            // 장기요양 (건강보험 전액 × 요율 / 2)
            long ltcTotal = calcAmount(health * 2, rates.getLongTermCare());
            long ltc = ltcTotal / 2;
            total += saveDeductionDetail(run, emp, company, "장기요양보험", ltc, deductionMap);

            // 고용보험
            long employment = calcAmount(monthlySalary, rates.getEmploymentInsurance());
            total += saveDeductionDetail(run, emp, company, "고용보험", employment, deductionMap);
        }

        // 소득세 + 지방소득세 (간이세액표)
        TaxWithholdingResDto tax = taxWithholdingService.getTax(year, monthlySalary, emp.getDependentsCount());
        if (tax != null) {
            total += saveDeductionDetail(run, emp, company, "근로소득세", tax.getIncomeTax(), deductionMap);
            total += saveDeductionDetail(run, emp, company, "근로지방소득세", tax.getLocalIncomeTax(), deductionMap);
        }
        //산재보험 (회사 100% 부담 — 사원 실수령액 무관, totalDeduction 가산 X)
        if (emp.getJobTypes() != null && emp.getJobTypes().getIndustrialAccidentRate() != null) {
            long industrialAccident = calcAmount(monthlySalary, emp.getJobTypes().getIndustrialAccidentRate());
            // INSERT만 — total에 가산 X
            saveDeductionDetail(run, emp, company, "산재보험", industrialAccident, deductionMap);
        }
        return total;
    }

    // PayItems 찾아서 PayrollDetails INSERT (없으면 skip)
    private long saveDeductionDetail(PayrollRuns run, Employee emp, Company company, String itemName, long amount, Map<String, PayItems> deductionMap) {
        if (amount <= 0) return 0L;

        PayItems item = deductionMap.get(itemName);
        if (item == null) {
            log.warn("[Payroll] PayItem '{}' 미존재 — skip", itemName);
            return 0L;
        }

        PayrollDetails detail = PayrollDetails.builder()
                .payrollRuns(run)
                .employee(emp)
                .payItems(item)
                .payItemName(itemName)
                .payItemType(PayItemType.DEDUCTION)
                .amount(amount)
                .company(company)
                .build();
        payrollDetailsRepository.save(detail);
        return amount;
    }

    // 보험료 계산 (EmpSalaryService에 있는 것과 동일)
    private long calcAmount(long base, BigDecimal rate) {
        if (rate == null) return 0L;
        return BigDecimal.valueOf(base).multiply(rate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    private long calcHalf(long base, BigDecimal rate) {
        if (rate == null) return 0L;
        return BigDecimal.valueOf(base).multiply(rate)
                .divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP)
                .longValue();
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

//    DC형 사원의 해당 급여대장의 퇴직연금 적립 데이터 생성
//    연간 임금 % 12
    private void createDcDeposits(PayrollRuns run, Company company){
        List<PayrollDetails> details = payrollDetailsRepository.findByPayrollRuns(run);
        Map<Long, List<PayrollDetails>> byEmp = details.stream()
                .collect(Collectors.groupingBy(d -> d.getEmployee().getEmpId()  ));
        for (Map.Entry<Long, List<PayrollDetails>> entry : byEmp.entrySet()){
            Long empId = entry.getKey();
            Employee emp = entry.getValue().get(0).getEmployee();

//            DC형만
            if(emp.getRetirementType() != RetirementType.DC) continue;
//            중복방지
            if (depositRepository.existsByPayrollRun_PayrollRunIdAndEmployee_EmpId(run.getPayrollRunId(), empId)) continue;

//            적립기준임금 = 해당 월지급합계(과세지급 총액)
            long baseAmount = entry.getValue().stream()
                    .filter(d-> d.getPayItemType() == PayItemType.PAYMENT)
                    .mapToLong(PayrollDetails::getAmount)
                    .sum();

//            월적립액 = 연간임금/12 -> 매월 지급시마다 1/12 적립
            long depositAmount = baseAmount / 12;

            RetirementPensionDeposits deposits = RetirementPensionDeposits.builder()
                    .employee(emp)
                    .baseAmount(baseAmount)
                    .depositAmount(depositAmount)
                    .depositDate(LocalDateTime.now())
                    .depStatus(DepStatus.COMPLETED)
                    .company(company)
                    .payrollRun(run)
                    .build();

            depositRepository.save(deposits);
        }
    }



    private boolean isHolidayForWorkGroup(UUID companyId, LocalDate date, WorkGroup wg) {
        // 회사 공휴일 캐시
        Set<LocalDate> monthHolidays = businessDayCalculator
                .getHolidaysInMonth(companyId, YearMonth.from(date));
        if (monthHolidays.contains(date)) return true;
        // 근무요일 비트마스크: 월=bit0 ~ 일=bit6. 비트가 안 켜져있으면 비근무일=휴일
        int bit = 1 << (date.getDayOfWeek().getValue() - 1);
        return wg == null || (wg.getGroupWorkDay() & bit) == 0;
    }

    private static final LocalTime NIGHT_START = LocalTime.of(22, 0);
    private static final LocalTime NIGHT_END   = LocalTime.of(6, 0);

    private long nightOverlapMinutes(LocalDateTime s, LocalDateTime e) {
        long total = 0L;
        LocalDate d = s.toLocalDate();
        while (!d.isAfter(e.toLocalDate())) {
            LocalDateTime ns = d.atTime(NIGHT_START);
            LocalDateTime ne = d.plusDays(1).atTime(NIGHT_END);
            total += overlapMin(s, e, ns, ne);
            d = d.plusDays(1);
        }
        return total;
    }

    private long overlapMin(LocalDateTime aS, LocalDateTime aE, LocalDateTime bS, LocalDateTime bE) {
        LocalDateTime s = aS.isAfter(bS) ? aS : bS;
        LocalDateTime e = aE.isBefore(bE) ? aE : bE;
        return e.isAfter(s) ? Duration.between(s, e).toMinutes() : 0L;
    }
}

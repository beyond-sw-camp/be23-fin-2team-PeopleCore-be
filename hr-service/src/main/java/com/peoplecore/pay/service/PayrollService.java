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
import com.peoplecore.pay.support.TaxableCalc;
import com.peoplecore.pay.transfer.BankTransferFileFactory;
import com.peoplecore.pay.transfer.BankTransferFileGenerator;
import com.peoplecore.salarycontract.domain.SalaryContract;
import com.peoplecore.salarycontract.domain.SalaryContractDetail;
import com.peoplecore.salarycontract.repository.SalaryContractDetailRepository;
import com.peoplecore.salarycontract.repository.SalaryContractRepository;
import com.peoplecore.vacation.entity.VacationPolicy;
import com.peoplecore.vacation.repository.VacationPolicyRepository;
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
    private final PayStubsRepository payStubsRepository;
    private final VacationPolicyRepository vacationPolicyRepository;
    private final LeaveAllowanceService leaveAllowanceService;
    private final LeaveAllowanceRepository leaveAllowanceRepository;



    @Autowired
    public PayrollService(PayrollRunsRepository payrollRunsRepository, PayrollDetailsRepository payrollDetailsRepository, EmployeeRepository employeeRepository, CompanyRepository companyRepository, SalaryContractRepository salaryContractRepository, SalaryContractDetailRepository salaryContractDetailRepository, PayItemsRepository payItemsRepository, PaySettingsRepository paySettingsRepository, BankTransferFileFactory bankTransferFileFactory, EmpAccountsRepository empAccountsRepository, CommuteRecordRepository commuteRecordRepository, OvertimeRequestRepository overtimeRequestRepository, BusinessDayCalculator businessDayCalculator, InsuranceRatesRepository insuranceRatesRepository, TaxWithholdingService taxWithholdingService, RetirementPensionDepositsRepository depositRepository, MySalaryCacheService mySalaryCacheService, PayrollEmpStatusRepository payrollEmpStatusRepository, PayStubsRepository payStubsRepository, VacationPolicyRepository vacationPolicyRepository, LeaveAllowanceService leaveAllowanceService, LeaveAllowanceRepository leaveAllowanceRepository) {
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
        this.payStubsRepository = payStubsRepository;
        this.vacationPolicyRepository = vacationPolicyRepository;
        this.leaveAllowanceService = leaveAllowanceService;
        this.leaveAllowanceRepository = leaveAllowanceRepository;
    }

///       급여대장 조회(특정 월)
    public PayrollRunResDto getPayroll(UUID companyId, String payYearMonth){

        PayrollRuns run = payrollRunsRepository.findByCompany_CompanyIdAndPayYearMonth(companyId, payYearMonth).orElseThrow(()-> new CustomException(ErrorCode.PAYROLL_NOT_FOUND));

        List<PayrollDetails> allDetails = payrollDetailsRepository.findByPayrollRuns(run);

//        사원별 그룹핑
        Map<Long, List<PayrollDetails>> detailsByEmp = allDetails.stream().collect(Collectors.groupingBy(d -> d.getEmployee().getEmpId()));

        // 사원별 PayrollEmpStatus 한 번에 조회
        Map<Long, PayrollEmpStatus> empStatusMap = payrollEmpStatusRepository
                .findByPayrollRuns_PayrollRunId(run.getPayrollRunId())
                .stream()
                .collect(Collectors.toMap(
                        s -> s.getEmployee().getEmpId(),
                        s -> s
                ));

        // OT 이미 적용된 사원 ID 집합
        Set<Long> appliedOtEmpIds = payrollDetailsRepository
                .findByPayrollRunsAndIsOvertimePayTrue(run)
                .stream()
                .map(d -> d.getEmployee().getEmpId())
                .collect(Collectors.toSet());

        List<PayrollEmpResDto> empList = detailsByEmp.entrySet().stream().map(entry -> {
            Employee emp = entry.getValue().get(0).getEmployee();
            List<PayrollDetails> details = entry.getValue();

            long pay = details.stream().filter(d-> d.getPayItemType() == PayItemType.PAYMENT)
                    .mapToLong(PayrollDetails::getAmount).sum();
            long deduction = details.stream()
                    .filter(d -> d.getPayItemType() == PayItemType.DEDUCTION)
                    .mapToLong(PayrollDetails::getAmount).sum();

        // 사원의 PayrollEmpStatus 1건 추출
        PayrollEmpStatus pes = empStatusMap.get(emp.getEmpId());
        String empStatusValue = pes != null ? pes.getStatus().name() : "CALCULATING";
        Long approvalDocIdValue = pes != null ? pes.getApprovalDocId() : null;

        // pendingOvertimeAmount 계산
        Long pendingOtValue;
        if (appliedOtEmpIds.contains(emp.getEmpId())) {
            pendingOtValue = 0L;   // 이미 적용 완료
        } else {
            ApprovedOvertimeResDto ot = getApprovedOvertime(companyId, run.getPayrollRunId(), emp.getEmpId());
            pendingOtValue = ot.getTotalAmount() > 0 ? ot.getTotalAmount() : null;
        }

        return PayrollEmpResDto.builder()
                .empId(emp.getEmpId())
                .empName(emp.getEmpName())
                .deptName(emp.getDept().getDeptName())
                .gradeName(emp.getGrade().getGradeName())
                .empType(emp.getEmpType().name())
                .status(run.getPayrollStatus().name())
                .payrollEmpStatus(empStatusValue)
                .approvalDocId(approvalDocIdValue)
                .empStatus(emp.getEmpStatus().name())
                .totalPay(pay)
                .totalDeduction(deduction)
                .netPay(pay-deduction)
                .unpaid(run.getPayrollStatus() == PayrollStatus.PAID ? 0L : pay - deduction)
                .pendingOvertimeAmount(pendingOtValue)
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
        Map<String, PayItems> deductionMap = payItemsRepository
                .findByCompany_CompanyIdAndPayItemTypeAndPayItemNameIn(companyId, PayItemType.DEDUCTION, DEDUCTION_ITEM_NAMES)
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

        LocalDate monthStart = payMonth.atDay(1);
        LocalDate monthEnd   = payMonth.atEndOfMonth();

        // 한 번에 모든 사원의 적용 가능 계약 조회
        List<Long> empIds = employees.stream().map(Employee::getEmpId).toList();
        Map<Long, SalaryContract> contractMap = salaryContractRepository
                .findActiveContractsByEmpIds(companyId, empIds, monthStart, monthEnd)
                .stream()
                .collect(Collectors.toMap(
                        c -> c.getEmployee().getEmpId(),
                        Function.identity(),
                        (a, b) -> a   // applyFrom DESC 정렬 → 첫 번째가 최신
                ));

        for (Employee emp : employees) {
            SalaryContract contract = contractMap.get(emp.getEmpId());
            if (contract == null) continue; // 이 달 적용 가능한 계약 없음 → skip

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
            Map<Long, PayItems> payItemMap = payItemsRepository.findByPayItemIdInAndCompany_CompanyId(payItemIds, companyId)
                    .stream()
                    .collect(Collectors.toMap
                            (PayItems::getPayItemId, Function.identity()));

//            항목중 과세대상 월소득 누계
            long taxableMonthly = 0L;

            for (SalaryContractDetail detail : contractDetails) {
                PayItems payItem = payItemMap.get(detail.getPayItemId());
                if (payItem == null) continue;

                long amt = detail.getAmount().longValue();

                PayrollDetails payrollDetail = PayrollDetails.builder()
                        .payrollRuns(run)
                        .employee(emp)
                        .payItems(payItem)
                        .payItemName(payItem.getPayItemName())
                        .payItemType(payItem.getPayItemType())
                        .amount(amt)
                        .company(company)
                        .build();

                payrollDetailsRepository.save(payrollDetail);

                if (payItem.getPayItemType() == PayItemType.PAYMENT) {
                    totalPay += amt;
                    taxableMonthly += TaxableCalc.taxablePart(payItem, amt);   // ★
                } else {
                    totalDeduction += amt;
                }
            }

            // 2. 4대보험 + 세금 자동 계산 (공제항목)
            long monthlySalary = (contract.getTotalAmount() != null)
                    ? contract.getTotalAmount()
                    .divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP)
                    .longValue()
                    : 0L;

            long calcDed = insertCalculatedDeductions(run, emp, company, monthlySalary, taxableMonthly, year, rates, deductionMap);
            totalDeduction += calcDed;
        }

//        합계 갱신
        run.updateTotals(employees.size(), totalPay, totalDeduction, totalPay - totalDeduction);

// 회사 연차 정책이 입사일 기준이면 이 달 입사기념일 도래자에 대해 자동 후보 + 산정
        VacationPolicy policy = vacationPolicyRepository.findByCompanyId(companyId).orElse(null);
        if (policy != null && policy.getPolicyBaseType() == VacationPolicy.PolicyBaseType.HIRE) {
            int month = Integer.parseInt(payYearMonth.substring(5, 7));

            // 1) 후보 INSERT
            leaveAllowanceService.createAnniversaryPendingRecords(companyId, year, month);

            // 2) PENDING 상태인 사원만 모아 자동 산정
            List<Long> pendingEmpIds = leaveAllowanceRepository
                    .findAllByCompanyAndYearAndType(companyId, year, AllowanceType.ANNIVERSARY)
                    .stream()
                    .filter(la -> la.getStatus() == AllowanceStatus.PENDING)
                    .filter(la -> la.getEmployee().getEmpHireDate() != null
                            && la.getEmployee().getEmpHireDate().getMonthValue() == month)
                    .map(la -> la.getEmployee().getEmpId())
                    .toList();

            if (!pendingEmpIds.isEmpty()) {
                try {
                    leaveAllowanceService.calculate(companyId, year, AllowanceType.ANNIVERSARY, pendingEmpIds);
                } catch (Exception e) {
                    log.warn("[연차수당 자동산정 실패] type=ANNIVERSARY, year={}, month={}, msg={}",
                            year, month, e.getMessage());
                }
            }
        }

        return getPayroll(companyId, payYearMonth);
    }

//    공제 항목명 목록
    private static final List<String> DEDUCTION_ITEM_NAMES = List.of("국민연금", "건강보험", "장기요양보험", "고용보험", "근로소득세", "근로지방소득세", "산재보험");


//    급여대장 - 사원동기화 (급여대장 생성이후(혹은 누락된) 등록된 사원(계약완료) 추가 등록)
    @Transactional
    public PayrollSyncResultResDto syncEmployees(UUID companyId, Long payrollRunId) {
        PayrollRuns run = findPayrollRun(companyId, payrollRunId);

        // CALCULATING / CONFIRMED / PENDING_APPROVAL 허용 (APPROVED/PAID 단계에선 차단)
         if (run.getPayrollStatus() == PayrollStatus.APPROVED
                || run.getPayrollStatus() == PayrollStatus.PAID) {
            throw new CustomException(ErrorCode.PAYROLL_STATUS_INVALID);
        }

        Company company = run.getCompany();
        YearMonth payMonth = YearMonth.parse(run.getPayYearMonth());
        int year = payMonth.getYear();

        // 현재 시점 급여 대상 사원
        List<Employee> currentEmps = employeeRepository.findAllForPayroll(companyId, payMonth);

        // 이미 들어와 있는 사원 ID
        Set<Long> existingEmpIds = payrollEmpStatusRepository
                .findByPayrollRuns_PayrollRunId(payrollRunId)
                .stream()
                .map(s -> s.getEmployee().getEmpId())
                .collect(Collectors.toSet());

        // 누락된 사원만 필터
        List<Employee> missing = currentEmps.stream()
                .filter(e -> !existingEmpIds.contains(e.getEmpId()))
                .toList();

        if (missing.isEmpty()) {
            return PayrollSyncResultResDto.builder()
                    .addedCount(0)
                    .totalEmployeesAfter(existingEmpIds.size())
                    .build();
        }

        // 보험요율 + 공제항목 마스터 (createPayroll 와 동일)
        InsuranceRates rates = insuranceRatesRepository
                .findByCompany_CompanyIdAndYear(companyId, year)
                .orElse(null);
        Map<String, PayItems> deductionMap = payItemsRepository
                .findByCompany_CompanyIdAndPayItemTypeAndPayItemNameIn(
                        companyId, PayItemType.DEDUCTION, DEDUCTION_ITEM_NAMES)
                .stream()
                .collect(Collectors.toMap(PayItems::getPayItemName, Function.identity()));

        int addedCount = 0;
        for (Employee emp : missing) {
            long[] sub = bootstrapEmployeeForRun(run, emp, company, companyId, year, rates, deductionMap);
            // 연봉계약 없는 사원은 sub = [0, 0] 반환되며 EmpStatus 도 안 만들어짐 → 카운트 제외
            if (sub[0] > 0 || sub[1] > 0) {
                addedCount++;
            }
        }

        // run 합계 재집계 (기존 사원 + 신규 추가분 모두 포함)
        payrollDetailsRepository.flush();
        List<PayrollDetails> all = payrollDetailsRepository.findByPayrollRuns(run);
        long totalPay = all.stream()
                .filter(d -> d.getPayItemType() == PayItemType.PAYMENT)
                .mapToLong(PayrollDetails::getAmount).sum();
        long totalDeduction = all.stream()
                .filter(d -> d.getPayItemType() == PayItemType.DEDUCTION)
                .mapToLong(PayrollDetails::getAmount).sum();
        int totalEmps = (int) payrollEmpStatusRepository.countByPayrollRuns_PayrollRunId(payrollRunId);
        run.updateTotals(totalEmps, totalPay, totalDeduction, totalPay - totalDeduction);

        log.info("[Payroll] 사원 동기화 완료 - runId={}, ym={}, added={}, totalAfter={}",
                payrollRunId, run.getPayYearMonth(), addedCount, totalEmps);

        return PayrollSyncResultResDto.builder()
                .addedCount(addedCount)
                .totalEmployeesAfter(totalEmps)
                .build();
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

///    급여 수정 - 항목금액 수정 시 4대보험/소득세 재계산
    @Transactional
    public void updateEmpDetails(UUID companyId, Long payrollRunId, Long empId, PayrollDetailUpdateReqDto reqDto){
        PayrollRuns run = findPayrollRun(companyId, payrollRunId);

        // 산정중 외엔 모두 수정 차단
        if (run.getPayrollStatus() != PayrollStatus.CALCULATING) {
            throw new CustomException(ErrorCode.PAYROLL_STATUS_INVALID);
        }

        // 해당 사원이 이미 CONFIRMED 면 차단
        PayrollEmpStatus empStatus = payrollEmpStatusRepository
                .findByPayrollRuns_PayrollRunIdAndEmployee_EmpId(payrollRunId, empId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
        if (empStatus.getStatus() == PayrollEmpStatusType.CONFIRMED) {
            throw new CustomException(ErrorCode.PAYROLL_EMP_ALREADY_CONFIRMED);
        }

        Employee emp = empStatus.getEmployee();
        Company company = run.getCompany();

        // 기존 detail 조회 → payItemId 로 매핑
        List<PayrollDetails> details = payrollDetailsRepository.findByPayrollRunsAndEmployee_EmpId(run, empId);
        Map<Long, PayrollDetails> detailMap = details.stream()
                .collect(Collectors.toMap(d -> d.getPayItems().getPayItemId(), Function.identity()));

        // 요청 항목 금액 갱신 (없는 항목은 무시)
        for (PayrollDetailUpdateReqDto.Item item : reqDto.getItems()) {
            PayrollDetails d = detailMap.get(item.getPayItemId());
            if (d == null) continue;
            d.updateAmount(item.getAmount());
        }

        //  해당 사원 공제 재계산 ───
        //    PAYMENT 항목들로부터 taxableMonthly 누계 → insertCalculatedDeductions 재실행
        InsuranceRates rates = insuranceRatesRepository
                .findByCompany_CompanyIdAndYear(companyId, Integer.parseInt(run.getPayYearMonth().substring(0, 4)))
                .orElse(null);
        Map<String, PayItems> deductionMap = payItemsRepository
                .findByCompany_CompanyIdAndPayItemTypeAndPayItemNameIn(companyId, PayItemType.DEDUCTION, DEDUCTION_ITEM_NAMES)
                .stream()
                .collect(Collectors.toMap(PayItems::getPayItemName, Function.identity()));

        long taxableMonthly = 0L;
        long monthlyTotal = 0L;
        for (PayrollDetails d : details) {
            if (d.getPayItemType() != PayItemType.PAYMENT) continue;
            long amt = d.getAmount() == null ? 0L : d.getAmount();
            monthlyTotal += amt;
            taxableMonthly += TaxableCalc.taxablePart(d.getPayItems(), amt);
        }

        int year = Integer.parseInt(run.getPayYearMonth().substring(0, 4));
        // 기존 메서드 재사용 (3.2 버전 시그니처)
        insertCalculatedDeductions(run, emp, company, monthlyTotal, taxableMonthly, year, rates, deductionMap);

        // 4. run 합계 재집계 — DB flush 후 다시 SELECT 해야 갱신된 공제 amount 가 반영됨
        payrollDetailsRepository.flush();
        List<PayrollDetails> all = payrollDetailsRepository.findByPayrollRuns(run);
        long totalPay = all.stream()
                .filter(d -> d.getPayItemType() == PayItemType.PAYMENT)
                .mapToLong(PayrollDetails::getAmount).sum();
        long totalDeduction = all.stream()
                .filter(d -> d.getPayItemType() == PayItemType.DEDUCTION)
                .mapToLong(PayrollDetails::getAmount).sum();
        run.updateTotals(run.getTotalEmployees(), totalPay, totalDeduction, totalPay - totalDeduction);
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
    public void applyApprovalResult(PayrollApprovalResultEvent event) {
        PayrollRuns run = findPayrollRun(event.getCompanyId(), event.getPayrollRunId());

//        approvalDocId 보완
        if (run.getApprovalDocId() == null && event.getApprovalDocId() != null) {
            run.bindApprovalDoc(event.getApprovalDocId());
            run.submitApproval(event.getApprovalDocId());
        }

        String status = event.getStatus();
        if ("APPROVED".equals(status)) {
            Long docId = event.getApprovalDocId();

            // 1) 이 docId 에 바인딩된 사원만 APPROVED 로 전이
            List<PayrollEmpStatus> bound = payrollEmpStatusRepository
                    .findByPayrollRuns_PayrollRunIdAndApprovalDocId(event.getPayrollRunId(), docId);
            bound.forEach(PayrollEmpStatus::approve);

            // 2) run 의 모든 사원이 APPROVED/PAID 면 run 도 APPROVED 로 전이
            boolean allEmpsApproved = payrollEmpStatusRepository
                    .findByPayrollRuns_PayrollRunId(event.getPayrollRunId())
                    .stream()
                    .allMatch(p -> p.getStatus() == PayrollEmpStatusType.APPROVED
                            || p.getStatus() == PayrollEmpStatusType.PAID);

            if (allEmpsApproved && run.getPayrollStatus() == PayrollStatus.PENDING_APPROVAL) {
                run.approve();
            }

            log.info("[PayrollService] 전자결재 승인 - runId={}, docId={}, 사원={}명, runApproved={}",
                    event.getPayrollRunId(), docId, bound.size(), allEmpsApproved);

            // 캐시 무효화 (기존 그대로)
            List<Long> empIds = bound.stream()
                    .map(p -> p.getEmployee().getEmpId()).toList();
            for (Long empId : empIds) {
                mySalaryCacheService.evictSalaryInfoCache(event.getCompanyId(), empId);
                mySalaryCacheService.evictStubListCache(event.getCompanyId(), empId);
                mySalaryCacheService.evictSeveranceEstimateCache(event.getCompanyId(), empId);
            }
        } else if ("REJECTED".equals(status) || "CANCELED".equals(status)) {
            Long docId = event.getApprovalDocId();
            List<PayrollEmpStatus> bound = payrollEmpStatusRepository
                    .findByPayrollRuns_PayrollRunIdAndApprovalDocId(event.getPayrollRunId(), docId);
            bound.forEach(PayrollEmpStatus::unbindApprovalDoc);

            // run 에 다른 진행 중 결재가 없을 때만 run 상태 되돌리기
            boolean anyStillInApproval = payrollEmpStatusRepository
                    .findByPayrollRuns_PayrollRunId(event.getPayrollRunId())
                    .stream()
                    .anyMatch(p -> p.getApprovalDocId() != null);

            if (!anyStillInApproval) {
                if ("REJECTED".equals(status)) run.rejectApproval();
                else run.cancelApproval();
            }

            log.info("[PayrollService] 결재 {} - runId={}, docId={}, unbound 사원={}명, runRollback={}",
                    status, event.getPayrollRunId(), docId, bound.size(), !anyStillInApproval);
        }
    }


///     지급 처리
    @Transactional
    public void processPayment(UUID companyId, Long payrollRunId, List<Long> empIds){
        PayrollRuns run = findPayrollRun(companyId, payrollRunId);// 대상 후보 — APPROVED 사원 전체 (empIds 비면) 또는 empIds ∩ APPROVED
        List<PayrollEmpStatus> approvedAll = payrollEmpStatusRepository
                .findByPayrollRuns_PayrollRunIdAndStatus(payrollRunId, PayrollEmpStatusType.APPROVED);

        List<PayrollEmpStatus> targets;
        if (empIds == null || empIds.isEmpty()) {
            targets = approvedAll;
        } else {
            Set<Long> requested = new HashSet<>(empIds);
            targets = approvedAll.stream()
                    .filter(p -> requested.contains(p.getEmployee().getEmpId()))
                    .toList();
        }

        if (targets.isEmpty()) {
            throw new CustomException(ErrorCode.NO_PAYABLE_EMPLOYEES);
        }

        // 대상 사원만 PAID 로 전이
        LocalDate now = LocalDate.now();
        targets.forEach(PayrollEmpStatus::markPaid);

        // 해당 사원의 PayStub 만 생성 (이미 있으면 스킵 — createPayStubsForEmployees 내부에서 중복 검사)
        Set<Long> targetEmpIds = targets.stream()
                .map(p -> p.getEmployee().getEmpId())
                .collect(Collectors.toSet());
        createPayStubsForEmployees(run, run.getCompany(), targetEmpIds);

        // run 의 모든 사원이 PAID 면 run 도 PAID 로 전이
        boolean allPaid = payrollEmpStatusRepository
                .findByPayrollRuns_PayrollRunId(payrollRunId)
                .stream()
                .allMatch(p -> p.getStatus() == PayrollEmpStatusType.PAID);

        if (allPaid && run.getPayrollStatus() == PayrollStatus.APPROVED) {
            run.markPaid(now);
        }

        log.info("[PayrollService] 지급처리 - runId={}, paid={}명, runMarkedPaid={}",
                payrollRunId, targets.size(), allPaid);
    }

// 급여명세서
    private void createPayStubsForEmployees(PayrollRuns run, Company company, Set<Long> empIds) {
        List<PayrollDetails> details = payrollDetailsRepository.findByPayrollRuns(run);
        Map<Long, List<PayrollDetails>> byEmp = details.stream()
                .filter(d -> empIds.contains(d.getEmployee().getEmpId()))
                .collect(Collectors.groupingBy(d -> d.getEmployee().getEmpId()));

        for (Map.Entry<Long, List<PayrollDetails>> entry : byEmp.entrySet()) {
            Long empId = entry.getKey();

            if (payStubsRepository.existsByEmpIdAndPayrollRunId(empId, run.getPayrollRunId())) continue;

            long totalPay = entry.getValue().stream()
                    .filter(d -> d.getPayItemType() == PayItemType.PAYMENT)
                    .mapToLong(PayrollDetails::getAmount).sum();
            long totalDeduction = entry.getValue().stream()
                    .filter(d -> d.getPayItemType() == PayItemType.DEDUCTION)
                    .mapToLong(PayrollDetails::getAmount).sum();
            long netPay = totalPay - totalDeduction;

            PayStubs stub = PayStubs.builder()
                    .empId(empId)
                    .payrollRunId(run.getPayrollRunId())
                    .payYearMonth(run.getPayYearMonth())
                    .totalPay(totalPay)
                    .totalDeduction(totalDeduction)
                    .netPay(netPay)
                    .sendStatus(SendStatus.PENDING)
                    .issuedAT(LocalDateTime.now())
                    .company(company)
                    .build();
            payStubsRepository.save(stub);
        }
    }


///    선택 사원 대량이체 파일생성
    public TransferFileResDto generateTransferFile(UUID companyId, Long payrollRunId, List<Long> empIds) {
        PayrollRuns run = findPayrollRun(companyId, payrollRunId);

        if (empIds == null || empIds.isEmpty()) {
            throw new CustomException(ErrorCode.NO_TRANSFER_TARGETS);
        }

        // 선택된 사원이 모두 APPROVED 또는 PAID 인지 검증
        Map<Long, PayrollEmpStatus> empStatusMap = payrollEmpStatusRepository
                .findByPayrollRuns_PayrollRunId(payrollRunId)
                .stream()
                .collect(Collectors.toMap(p -> p.getEmployee().getEmpId(), p -> p));

        for (Long empId : empIds) {
            PayrollEmpStatus pes = empStatusMap.get(empId);
            if (pes == null) {
                throw new CustomException(ErrorCode.PAYROLL_EMP_NOT_FOUND);
            }
            PayrollEmpStatusType st = pes.getStatus();
            if (st != PayrollEmpStatusType.APPROVED && st != PayrollEmpStatusType.PAID) {
                throw new CustomException(ErrorCode.PAYROLL_EMP_NOT_APPROVED);
            }
        }
//    CompanyPaySettings 조회, PayrollDetails 조회, 이체파일 생성
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

        PayrollRuns run = findPayrollRun(companyId, payrollRunId);
        YearMonth payMonth = YearMonth.parse(run.getPayYearMonth());
        LocalDate monthStart = payMonth.atDay(1);
        LocalDate monthEnd   = payMonth.atEndOfMonth();

//        최신 (유효한) 연봉계약의 통상임금
        SalaryContract contract = salaryContractRepository
                .findActiveContractsByEmpIds(companyId, List.of(empId), monthStart, monthEnd)
                .stream().findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.SALARY_CONTRACT_NOT_FOUND));

        long monthlySalary = contract.getTotalAmount().divide(BigDecimal.valueOf(12), 0, RoundingMode.FLOOR).longValue();

//        시급 = 통상임금(월) % 209
        long hourlyWage = Math.round((double) monthlySalary / 209);

//        일당 = 시급 * 일근무시간(사원별 근무그룹)
        Employee emp = employeeRepository.findById(empId).orElseThrow(()-> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
        WorkGroup empWorkHour = emp.getWorkGroup();
        Duration workTime = Duration.between(empWorkHour.getGroupStartTime(), empWorkHour.getGroupEndTime()).minus(Duration.between(empWorkHour.getGroupBreakStart(), empWorkHour.getGroupBreakEnd()));

        long dailyWorkMinutes = workTime.toMinutes();   //분 단위

        long dailyWage = Math.round(hourlyWage * (dailyWorkMinutes / 60.0));
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

// 휴일 8시간 초과분 분리
        long holNormalMin = Math.min(totalHolidayMin, 8L * 60);  // 휴일 8h 이내
        long holOverMin   = Math.max(0L, totalHolidayMin - 8L * 60);  // 휴일 8h 초과

// 정상분 + 가산분 한꺼번에 산정
        long extendedPay = Math.round(hourlyWage * 1.5 * totalExtMin / 60.0);   // /60 : 분-> 시로 변환

        long holidayPay  = Math.round(
                hourlyWage * 1.5 * holNormalMin / 60.0
                        + hourlyWage * 2.0 * holOverMin   / 60.0
        );

// 야간은 가산분(0.5)만 — 정상분은 ext/hol 에서 처리됨
        long nightPay = Math.round(hourlyWage * 0.5 * totalNightMin / 60.0);

        long totalAmount = extendedPay + holidayPay + nightPay;

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
//   4대보험·소득세 base 모두 비과세 차감된 taxableBase 사용 (법령상 원칙)
    public CalcDeductionResDto calcDeductions(UUID companyId, CalcDeductionReqDto reqDto){

        long totalPay = reqDto.getTotalPay();
        // 프론트가 taxablePay 를 안 보내면 totalPay 로 fallback (= 전액 과세)
        long taxableBase = (reqDto.getTaxablePay() != null) ? reqDto.getTaxablePay() : totalPay;
        long base = Math.max(0L, taxableBase);

        // 해당연도 보험요율 조회
        int currentYear = LocalDate.now().getYear();
        InsuranceRates rates = insuranceRatesRepository
                .findByCompany_CompanyIdAndYear(companyId, currentYear)
                .orElseThrow(()-> new CustomException(ErrorCode.INSURANCE_RATES_NOT_FOUND));

        // 4대보험 근로자 부담분 — base(=비과세 차감 후) 기준
        long pensionBase = base;
        if (pensionBase > rates.getPensionUpperLimit()) pensionBase = rates.getPensionUpperLimit();
        if (pensionBase < rates.getPensionLowerLimit()) pensionBase = rates.getPensionLowerLimit();
        long pension = Math.round(pensionBase * rates.getNationalPension().doubleValue() / 2);

        long health = Math.round(base * rates.getHealthInsurance().doubleValue() / 2);
        long healthTotal = Math.round(base * rates.getHealthInsurance().doubleValue());
        long ltc = Math.round(healthTotal * rates.getLongTermCare().doubleValue() / 2);
        long employment = Math.round(base * rates.getEmploymentInsurance().doubleValue());

        // 소득세
        Employee emp = employeeRepository.findById(reqDto.getEmpId())
                .orElseThrow(()-> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
        TaxWithholdingResDto tax = taxWithholdingService.getTax(
                currentYear, base, emp.getDependentsCount());

        long incomeTax = tax != null ? tax.getIncomeTax() : 0L;
        long localIncomeTax = tax != null ? tax.getLocalIncomeTax() : 0L;

        long totalDeduction = pension + health + ltc + employment + incomeTax + localIncomeTax;

        return CalcDeductionResDto.builder()
                .nationalPension(pension)
                .healthInsurance(health)
                .longTermCare(ltc)
                .employmentInsurance(employment)
                .incomeTax(incomeTax)
                .localIncomeTax(localIncomeTax)
                .totalDeduction(totalDeduction)
                .netPay(totalPay - totalDeduction)   // 실수령액은 totalPay - 공제 그대로
                .build();
    }



    // 4대보험 + 세금 자동 계산 후 PayrollDetails INSERT
    // base 정책:
    //  - 4대보험(국민연금/건강/장기요양/고용) → 보수월액에서 비과세 제외(법령상 원칙) → taxableMonthly 기준
    //  - 산재보험 임금총액 → 비과세 제외 → taxableMonthly 기준
    //  - 근로소득세 / 지방소득세 → 비과세 제외 → taxableMonthly 기준
    private long insertCalculatedDeductions(PayrollRuns run,
                                            Employee emp,
                                            Company company,
                                            long monthlySalary,    // 보수 총액(과세+비과세) — 현행 호환용 파라미터, 본 메서드에선 미사용
                                            long taxableMonthly,   // ★ 비과세 차감 후 base
                                            int year,
                                            InsuranceRates rates,
                                            Map<String, PayItems> deductionMap) {
        if (monthlySalary <= 0 && taxableMonthly <= 0) return 0L;

        long base = Math.max(0L, taxableMonthly);   // 모든 보험·세금 base
        long total = 0L;

        if (rates != null) {
            // 국민연금 (상하한 적용)
            long pensionBase = base;
            if (rates.getPensionUpperLimit() != null && pensionBase > rates.getPensionUpperLimit()) {
                pensionBase = rates.getPensionUpperLimit();
            }
            if (rates.getPensionLowerLimit() != null && pensionBase < rates.getPensionLowerLimit()) {
                pensionBase = rates.getPensionLowerLimit();
            }
            long pension = calcHalf(pensionBase, rates.getNationalPension());
            total += saveDeductionDetail(run, emp, company, "국민연금", pension, deductionMap);

            // 건강보험
            long health = calcHalf(base, rates.getHealthInsurance());
            total += saveDeductionDetail(run, emp, company, "건강보험", health, deductionMap);

            // 장기요양 (건강보험 전액 × 요율 / 2)
            long ltcTotal = calcAmount(health * 2, rates.getLongTermCare());
            long ltc = ltcTotal / 2;
            total += saveDeductionDetail(run, emp, company, "장기요양보험", ltc, deductionMap);

            // 고용보험
            long employment = calcAmount(base, rates.getEmploymentInsurance());
            total += saveDeductionDetail(run, emp, company, "고용보험", employment, deductionMap);
        }

        // 소득세 + 지방소득세
        TaxWithholdingResDto tax =
                taxWithholdingService.getTax(year, base, emp.getDependentsCount());
        if (tax != null) {
            total += saveDeductionDetail(run, emp, company, "근로소득세", tax.getIncomeTax(), deductionMap);
            total += saveDeductionDetail(run, emp, company, "근로지방소득세", tax.getLocalIncomeTax(), deductionMap);
        }

        // 산재보험 (회사 100% 부담 — totalDeduction 가산 X)
        if (emp.getJobTypes() != null && emp.getJobTypes().getIndustrialAccidentRate() != null) {
            long industrialAccident = calcAmount(base, emp.getJobTypes().getIndustrialAccidentRate());
            saveDeductionDetail(run, emp, company, "산재보험", industrialAccident, deductionMap);
        }
        return total;
    }


    // PayItems 찾아서 PayrollDetails INSERT (없으면 skip)
    private long saveDeductionDetail(PayrollRuns run, Employee emp, Company company, String itemName, long amount, Map<String, PayItems> deductionMap) {

        PayItems item = deductionMap.get(itemName);
        if (item == null) {
            log.warn("[Payroll] PayItem '{}' 미존재 — skip", itemName);
            return 0L;
        }
        // 기존 row 조회
        Optional<PayrollDetails> existing = payrollDetailsRepository
                .findByPayrollRunsAndEmployee_EmpIdAndPayItems_PayItemId(run, emp.getEmpId(), item.getPayItemId());

        if (amount <= 0) {
            // 기존에 있던 항목인데 이번 재계산 결과 0이면 0으로 갱신 (행 자체는 유지)
            existing.ifPresent(d -> d.updateAmount(0L));
            return 0L;
        }

        if (existing.isPresent()) {
            existing.get().updateAmount(amount);
        } else {
            payrollDetailsRepository.save(PayrollDetails.builder()
                    .payrollRuns(run)
                    .employee(emp)
                    .payItems(item)
                    .payItemName(itemName)
                    .payItemType(PayItemType.DEDUCTION)
                    .amount(amount)
                    .company(company)
                    .build());
        }
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


//
////    급여명세서 생성
//    private void createPayStubs(PayrollRuns run, Company company) {
//        List<PayrollDetails> details = payrollDetailsRepository.findByPayrollRuns(run);
//        Map<Long, List<PayrollDetails>> byEmp = details.stream()
//                .collect(Collectors.groupingBy(d -> d.getEmployee().getEmpId()));
//
//        for (Map.Entry<Long, List<PayrollDetails>> entry : byEmp.entrySet()) {
//            Long empId = entry.getKey();
//
//            // 중복 방지
//            if (payStubsRepository.existsByEmpIdAndPayrollRunId(empId, run.getPayrollRunId())) continue;
//
//            long totalPay = entry.getValue().stream()
//                    .filter(d -> d.getPayItemType() == PayItemType.PAYMENT)
//                    .mapToLong(PayrollDetails::getAmount).sum();
//            long totalDeduction = entry.getValue().stream()
//                    .filter(d -> d.getPayItemType() == PayItemType.DEDUCTION)
//                    .mapToLong(PayrollDetails::getAmount).sum();
//            long netPay = totalPay - totalDeduction;
//
//            PayStubs stub = PayStubs.builder()
//                    .empId(empId)
//                    .payrollRunId(run.getPayrollRunId())
//                    .payYearMonth(run.getPayYearMonth())
//                    .totalPay(totalPay)
//                    .totalDeduction(totalDeduction)
//                    .netPay(netPay)
//                    .sendStatus(SendStatus.PENDING)   // 초기 상태 (전송 대기)
//                    .issuedAT(LocalDateTime.now())
//                    .company(company)
//                    .build();
//            payStubsRepository.save(stub);
//        }
//    }


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

//    생성된 급여대장에 새로운 사원 추가시, 한 사원에 대해
//    PayrollEmpStatus + PayrollDetails(지급) + 4대보험/소득세 detail 생성
    private long[] bootstrapEmployeeForRun(PayrollRuns run,
                                           Employee emp,
                                           Company company,
                                           UUID companyId,
                                           int year,
                                           InsuranceRates rates,
                                           Map<String, PayItems> deductionMap) {
        // 해당 월에 적용 가능한 최신 계약
        YearMonth payMonth = YearMonth.parse(run.getPayYearMonth());
        LocalDate monthStart = payMonth.atDay(1);
        LocalDate monthEnd   = payMonth.atEndOfMonth();

        // 최신 연봉계약 조회
        SalaryContract contract = salaryContractRepository
                .findActiveContractsByEmpIds(companyId, List.of(emp.getEmpId()), monthStart, monthEnd)
                .stream().findFirst()
                .orElse(null);
        if (contract == null) return new long[]{0L, 0L};

        // 사원별 산정 상태 (기본 CALCULATING)
        payrollEmpStatusRepository.save(PayrollEmpStatus.builder()
                .payrollRuns(run)
                .employee(emp)
                .status(PayrollEmpStatusType.CALCULATING)
                .companyId(companyId)
                .build());

        // 1. 계약 상세 항목 (지급항목)
        List<SalaryContractDetail> contractDetails =
                salaryContractDetailRepository.findByContract_ContractId(contract.getContractId());

        List<Long> payItemIds = contractDetails.stream()
                .map(SalaryContractDetail::getPayItemId)
                .toList();
        Map<Long, PayItems> payItemMap = payItemsRepository
                .findByPayItemIdInAndCompany_CompanyId(payItemIds, companyId)
                .stream()
                .collect(Collectors.toMap(PayItems::getPayItemId, Function.identity()));

        long taxableMonthly = 0L;
        long payAdded = 0L;
        long deductionAdded = 0L;

        for (SalaryContractDetail detail : contractDetails) {
            PayItems payItem = payItemMap.get(detail.getPayItemId());
            if (payItem == null) continue;

            long amt = detail.getAmount().longValue();

            payrollDetailsRepository.save(PayrollDetails.builder()
                    .payrollRuns(run)
                    .employee(emp)
                    .payItems(payItem)
                    .payItemName(payItem.getPayItemName())
                    .payItemType(payItem.getPayItemType())
                    .amount(amt)
                    .company(company)
                    .build());

            if (payItem.getPayItemType() == PayItemType.PAYMENT) {
                payAdded += amt;
                taxableMonthly += TaxableCalc.taxablePart(payItem, amt);
            } else {
                deductionAdded += amt;
            }
        }

        // 2. 4대보험 + 세금 자동 계산
        long monthlySalary = (contract.getTotalAmount() != null)
                ? contract.getTotalAmount()
                  .divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP)
                  .longValue()
                : 0L;

        long calcDed = insertCalculatedDeductions(
                run, emp, company, monthlySalary, taxableMonthly, year, rates, deductionMap);
        deductionAdded += calcDed;

        return new long[]{payAdded, deductionAdded};
    }
}

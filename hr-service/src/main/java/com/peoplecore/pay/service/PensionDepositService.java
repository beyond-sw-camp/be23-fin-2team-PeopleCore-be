package com.peoplecore.pay.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.EmpRetirementAccount;
import com.peoplecore.pay.domain.PayrollDetails;
import com.peoplecore.pay.domain.PayrollRuns;
import com.peoplecore.pay.domain.RetirementPensionDeposits;
import com.peoplecore.pay.dtos.*;
import com.peoplecore.pay.enums.DepStatus;
import com.peoplecore.pay.enums.PayItemType;
import com.peoplecore.pay.enums.PayrollStatus;
import com.peoplecore.pay.enums.RetirementType;
import com.peoplecore.pay.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@Slf4j
public class PensionDepositService {

    private final RetirementPensionDepositsRepository retirementPensionDepositsRepository;
    private final EmployeeRepository employeeRepository;
    private final SeverancePaysRepository severancePaysRepository;
    private final CompanyRepository companyRepository;
    private final PayrollDetailsRepository payrollDetailsRepository;
    private final PayrollRunsRepository payrollRunsRepository;
    private final EmpRetirementAccountRepository empRetirementAccountRepository;
    private final MySalaryCacheService mySalaryCacheService;

    @Autowired
    public PensionDepositService(RetirementPensionDepositsRepository retirementPensionDepositsRepository, EmployeeRepository employeeRepository, SeverancePaysRepository severancePaysRepository, CompanyRepository companyRepository, PayrollDetailsRepository payrollDetailsRepository, PayrollRunsRepository payrollRunsRepository, EmpRetirementAccountRepository empRetirementAccountRepository, MySalaryCacheService mySalaryCacheService) {
        this.retirementPensionDepositsRepository = retirementPensionDepositsRepository;
        this.employeeRepository = employeeRepository;
        this.severancePaysRepository = severancePaysRepository;
        this.companyRepository = companyRepository;
        this.payrollDetailsRepository = payrollDetailsRepository;
        this.payrollRunsRepository = payrollRunsRepository;
        this.empRetirementAccountRepository = empRetirementAccountRepository;
        this.mySalaryCacheService = mySalaryCacheService;
    }


//    1. 목록조회
    public PensionDepositSummaryResDto getDepositList(
            UUID companyId, String fromYm, String toYm, Long empId, Long deptId, DepStatus status, Pageable pageable){

        Page<PensionDepositResDto> deposits = retirementPensionDepositsRepository.search(companyId,fromYm,toYm,empId,deptId,status,pageable);

        Integer totalEmployees = retirementPensionDepositsRepository.countDistinctEmployees(companyId, fromYm, toYm, status);
        Long totalDepositAmount = retirementPensionDepositsRepository.sumDepositAmount(companyId, fromYm, toYm, status);
        Long grandTotalDeposited = retirementPensionDepositsRepository.grandTotalDeposited(companyId);

//        월 수, 평균적립액 계산
        long months = calcMonthsBetween(fromYm, toYm);
        Long monthlyAverage = months > 0 ? totalDepositAmount / months : 0L;

        return PensionDepositSummaryResDto.builder()
                .totalEmployees(totalEmployees)
                .totalDepositAmount(totalDepositAmount)
                .monthlyAverage(monthlyAverage)
                .grandTotalDeposited(grandTotalDeposited)
                .deposits(deposits)
                .build();
    }

//    2. 사원별 이력조회
    public PensionDepositEmployeeResDto getEmployeeDeposits(UUID companyId, Long empId, String fromYm, String toYm){

        Employee emp = employeeRepository.findByEmpIdAndCompany_CompanyId(empId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        List<PensionDepositResDto> deposits = retirementPensionDepositsRepository.findByEmpId(companyId, empId, fromYm, toYm);
        Long totalDeposited = severancePaysRepository.sumDcDepositedTotal(empId, companyId) ;

        return PensionDepositEmployeeResDto.builder()
                .empId(emp.getEmpId())
                .empName(emp.getEmpName())
                .deptName(emp.getDept() != null ? emp.getDept().getDeptName() : null)
                .retirementType(emp.getRetirementType() != null ? emp.getRetirementType().name() : null)
                .totalDeposited(totalDeposited)
                .deposits(deposits)
                .build();
    }


//  3. 수동 적립 등록
    @Transactional
    public PensionDepositResDto createManualDeposit(UUID companyId, Long adminEmpId, PensionDepositCreateReqDto req) {
        Employee emp = employeeRepository.findByEmpIdAndCompany_CompanyId(req.getEmpId(), companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        // DC형 검증
        if (emp.getRetirementType() != RetirementType.DC) {
            throw new CustomException(ErrorCode.EMPLOYEE_NOT_DC);
        }

        // 중복 체크: 같은 사원·같은 월·COMPLETED 상태 이미 있으면 거부
        if (retirementPensionDepositsRepository.existsByEmployee_EmpIdAndPayYearMonthAndDepStatus(
                req.getEmpId(), req.getPayYearMonth(), DepStatus.COMPLETED)) {
            throw new CustomException(ErrorCode.DEPOSIT_ALREADY_EXISTS);
        }

        RetirementPensionDeposits deposit = RetirementPensionDeposits.builder()
                .employee(emp)
                .baseAmount(req.getBaseAmount())
                .depositAmount(req.getDepositAmount())
                .payYearMonth(req.getPayYearMonth())
                .depositDate(LocalDateTime.now())
                .depStatus(req.getDepStatus())
                .company(companyRepository.getReferenceById(companyId))
                .payrollRun(null)   // 수동적립은 null
                .isManual(true)
                .reason(req.getReason())
                .createdBy(adminEmpId)
                .build();

        RetirementPensionDeposits saved = retirementPensionDepositsRepository.save(deposit);

        // 사원 본인 화면용 캐시 무효화 (퇴직연금 적립내역 / 예상퇴직금)
        mySalaryCacheService.evictPensionCache(companyId, req.getEmpId());
        mySalaryCacheService.evictSeveranceEstimateCache(companyId, req.getEmpId());

        log.info("[PensionDeposit 수동등록] depId={}, empId={}, payYearMonth={}, amount={}, by={}",
                saved.getDepId(), req.getEmpId(), req.getPayYearMonth(), req.getDepositAmount(), adminEmpId);

        return toRes(saved, emp);
    }


//    적립 취소
    @Transactional
    public void cancelDeposit(UUID companyId, Long adminEmpId, Long depId, String reason) {
        RetirementPensionDeposits deposit = retirementPensionDepositsRepository.findByDepIdAndCompany_CompanyId(depId, companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.DEPOSIT_NOT_FOUND));

        if (deposit.getDepStatus() == DepStatus.CANCELED) {
            throw new CustomException(ErrorCode.DEPOSIT_ALREADY_CANCELED);
        }

        deposit.cancel(adminEmpId, reason);

        Long empIdForEvict = deposit.getEmployee().getEmpId();
        mySalaryCacheService.evictPensionCache(companyId, empIdForEvict);
        mySalaryCacheService.evictSeveranceEstimateCache(companyId, empIdForEvict);

        log.warn("[PensionDeposit 취소] depId={}, empId={}, by={}, reason={}",
                depId, deposit.getEmployee().getEmpId(), adminEmpId, reason);
    }


    // 5. 월별 요약
    public List<MonthlyDepositSummaryDto> getMonthlySummary(UUID companyId, Integer year) {
        int targetYear = year != null ? year : java.time.LocalDate.now().getYear();
        return retirementPensionDepositsRepository.monthlySummary(companyId, targetYear);
    }


    // 6. 사원별 집계 (화면 메인 테이블용(리스트) - 사원당 1행으로 묶음)
    public PensionDepositByEmployeeSummaryResDto getDepositByEmployee(
            UUID companyId, String fromYm, String toYm,
            String search, Long deptId, DepStatus status) {

        List<PensionDepositByEmployeeResDto> rows = retirementPensionDepositsRepository.searchByEmployee(companyId, fromYm, toYm, search, deptId, status);

        // 요약 카드 (기존 목록 API와 동일)
        Integer totalEmployees = rows.size();
        Long totalDepositAmount = rows.stream().mapToLong(PensionDepositByEmployeeResDto::getTotalAmount).sum();
        Long grandTotalDeposited = retirementPensionDepositsRepository.grandTotalDeposited(companyId);
        long months = calcMonthsBetween(fromYm, toYm);
        Long monthlyAverage = months > 0 ? totalDepositAmount / months : 0L;

        // 적립예정(SCHEDULED) 집계 — UI 필터(status) 와 무관하게 항상 계산 (운영자가 처리 대기 인지)
        Integer scheduledCount = retirementPensionDepositsRepository
                .countDistinctEmployees(companyId, fromYm, toYm, DepStatus.SCHEDULED);
        Long scheduledAmount = retirementPensionDepositsRepository
                .sumDepositAmount(companyId, fromYm, toYm, DepStatus.SCHEDULED);
        List<String> scheduledMonths = retirementPensionDepositsRepository      // 추가
                .distinctScheduledMonths(companyId, fromYm, toYm);

        return PensionDepositByEmployeeSummaryResDto.builder()
                .totalEmployees(totalEmployees)
                .totalDepositAmount(totalDepositAmount)
                .monthlyAverage(monthlyAverage)
                .grandTotalDeposited(grandTotalDeposited)
                .scheduledCount(scheduledCount != null ? scheduledCount : 0)
                .scheduledAmount(scheduledAmount != null ? scheduledAmount : 0L)
                .scheduledMonths(scheduledMonths != null ? scheduledMonths : List.of())
                .employees(rows)
                .build();
    }

//    7. 명세 엑셀 다운로드
    public record PensionDepositExcelResult(byte[] bytes, int rowCount) {}

    public PensionDepositExcelResult buildPeriodExcel(UUID companyId, String fromYm, String toYm) {
        // 1. 기간 내 COMPLETED 적립 조회 (사원/부서 join)
        List<RetirementPensionDeposits> deposits = retirementPensionDepositsRepository
                .findCompletedByCompanyAndYearMonthBetween(companyId, fromYm, toYm);

        if (deposits.isEmpty()) {
            throw new CustomException(ErrorCode.DEPOSIT_NOT_FOUND);
        }

        // 2. 사원별 EmpRetirementAccount (사업자, 계좌번호) 조회 — empId → account 맵
        List<Long> empIds = deposits.stream().map(d -> d.getEmployee().getEmpId()).distinct().toList();
        Map<Long, EmpRetirementAccount> accountMap = empRetirementAccountRepository
                .findAllByCompany_CompanyIdAndEmployee_EmpIdIn(companyId, empIds)
                .stream()
                .collect(Collectors.toMap(a -> a.getEmployee().getEmpId(), a -> a));

        // 3. 사원별 합산 (기간 누계)
        Map<Long, EmpAggregate> aggMap = new LinkedHashMap<>();
        for (RetirementPensionDeposits d : deposits) {
            Long empId = d.getEmployee().getEmpId();
            EmpAggregate agg = aggMap.computeIfAbsent(empId, k -> new EmpAggregate(d.getEmployee()));
            agg.totalBase += d.getBaseAmount();
            agg.totalDeposit += d.getDepositAmount();
            agg.monthCount++;
        }

        // 4. POI 로 엑셀 생성 (Sheet 1: 사원별 합산, Sheet 2: 월별 상세)
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // 공통 스타일
            CellStyle headerStyle = wb.createCellStyle();
            Font bold = wb.createFont(); bold.setBold(true); headerStyle.setFont(bold);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            String periodLabel = fromYm.equals(toYm) ? fromYm : (fromYm + " ~ " + toYm);

            // ── Sheet 1: 사원별 합산 (사업자 송금/회계 결산용) ──
            Sheet summarySheet = wb.createSheet(periodLabel + " 합산");
            String[] summaryHeaders = {"사번", "사원명", "부서", "사업자", "DC계좌번호", "적립월수", "적립기준임금 누계", "적립금액 누계"};
            writeHeaderRow(summarySheet, summaryHeaders, headerStyle);
            long grandTotal = 0L;
            int sr = 1;
            for (EmpAggregate agg : aggMap.values()) {
                EmpRetirementAccount acc = accountMap.get(agg.emp.getEmpId());
                Row row = summarySheet.createRow(sr++);
                row.createCell(0).setCellValue(agg.emp.getEmpNum());
                row.createCell(1).setCellValue(agg.emp.getEmpName());
                row.createCell(2).setCellValue(agg.emp.getDept() != null ? agg.emp.getDept().getDeptName() : "");
                row.createCell(3).setCellValue(acc != null ? acc.getPensionProvider() : "");
                row.createCell(4).setCellValue(acc != null ? acc.getAccountNumber() : "");
                row.createCell(5).setCellValue(agg.monthCount);
                row.createCell(6).setCellValue(agg.totalBase);
                row.createCell(7).setCellValue(agg.totalDeposit);
                grandTotal += agg.totalDeposit;
            }
            // 합계 행
            Row totalRow = summarySheet.createRow(sr);
            Cell totalLabel = totalRow.createCell(0);
            totalLabel.setCellValue("합계");
            totalLabel.setCellStyle(headerStyle);
            Cell totalAmount = totalRow.createCell(7);
            totalAmount.setCellValue(grandTotal);
            totalAmount.setCellStyle(headerStyle);
            for (int i = 0; i < summaryHeaders.length; i++) summarySheet.autoSizeColumn(i);

            // ── Sheet 2: 월별 상세 (감사/검증용) ──
            Sheet detailSheet = wb.createSheet("월별 상세");
            String[] detailHeaders = {"적립월", "사번", "사원명", "부서", "사업자", "DC계좌번호", "적립기준임금", "적립금액"};
            writeHeaderRow(detailSheet, detailHeaders, headerStyle);
            int dr = 1;
            for (RetirementPensionDeposits d : deposits) {
                Employee emp = d.getEmployee();
                EmpRetirementAccount acc = accountMap.get(emp.getEmpId());
                Row row = detailSheet.createRow(dr++);
                row.createCell(0).setCellValue(d.getPayYearMonth());
                row.createCell(1).setCellValue(emp.getEmpNum());
                row.createCell(2).setCellValue(emp.getEmpName());
                row.createCell(3).setCellValue(emp.getDept() != null ? emp.getDept().getDeptName() : "");
                row.createCell(4).setCellValue(acc != null ? acc.getPensionProvider() : "");
                row.createCell(5).setCellValue(acc != null ? acc.getAccountNumber() : "");
                row.createCell(6).setCellValue(d.getBaseAmount());
                row.createCell(7).setCellValue(d.getDepositAmount());
            }
            for (int i = 0; i < detailHeaders.length; i++) detailSheet.autoSizeColumn(i);

            wb.write(out);
            return new PensionDepositExcelResult(out.toByteArray(), deposits.size());
        } catch (IOException e) {
            throw new CustomException(ErrorCode.EXCEL_GENERATION_FAILED);
        }
    }


//    8. 퇴직연금 산정만 (SCHEDULED 상태로)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int createScheduledDeposits(UUID companyId, String payYearMonth) {
        PayrollRuns run = payrollRunsRepository
                .findByCompany_CompanyIdAndPayYearMonth(companyId, payYearMonth)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYROLL_NOT_FOUND));

        if (run.getPayrollStatus() != PayrollStatus.PAID) {
            throw new CustomException(ErrorCode.PAYROLL_STATUS_INVALID);
        }

        List<PayrollDetails> details = payrollDetailsRepository.findByPayrollRuns(run);
        Map<Long, List<PayrollDetails>> byEmp = details.stream()
                .collect(Collectors.groupingBy(d -> d.getEmployee().getEmpId()));

        int created = 0;
        for (Map.Entry<Long, List<PayrollDetails>> entry : byEmp.entrySet()) {
            Employee emp = entry.getValue().get(0).getEmployee();
            if (emp.getRetirementType() != RetirementType.DC) continue;

            // 이미 SCHEDULED 또는 COMPLETED 가 있으면 스킵 (자동/수동 중복 방지)
            if (retirementPensionDepositsRepository
                    .existsByPayrollRun_PayrollRunIdAndEmployee_EmpId(run.getPayrollRunId(), emp.getEmpId())) {
                continue;
            }

            long baseAmount = entry.getValue().stream()
                    .filter(d -> d.getPayItemType() == PayItemType.PAYMENT)
                    .mapToLong(PayrollDetails::getAmount)
                    .sum();
            long depositAmount = baseAmount / 12;

            RetirementPensionDeposits sched = RetirementPensionDeposits.builder()
                    .employee(emp)
                    .payYearMonth(payYearMonth)
                    .baseAmount(baseAmount)
                    .depositAmount(depositAmount)
                    .depStatus(DepStatus.SCHEDULED)
                    .company(run.getCompany())
                    .payrollRun(run)
                    .build();
            RetirementPensionDeposits saved = retirementPensionDepositsRepository.saveAndFlush(sched);
            log.info("[DEBUG] saved depId={}, empId={}, status={}",
                    saved.getDepId(), emp.getEmpId(), saved.getDepStatus());
            created++;

        }
        return created;
    }

//    9. 퇴직연금 산정-> 적립 처리 (SCHEDULED -> COMPLETED)
    @Transactional
    public int createMonthlyDeposits(UUID companyId, String payYearMonth) {
        PayrollRuns run = payrollRunsRepository
                .findByCompany_CompanyIdAndPayYearMonth(companyId, payYearMonth)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYROLL_NOT_FOUND));

        if (run.getPayrollStatus() != PayrollStatus.PAID) {
            throw new CustomException(ErrorCode.PAYROLL_STATUS_INVALID);
        }

        Set<Long> affectedEmpIds = new java.util.HashSet<>();

        // 1. run 의 기존 SCHEDULED -> COMPLETED 로 승격
        List<RetirementPensionDeposits> scheduled = retirementPensionDepositsRepository
                .findByPayrollRun_PayrollRunIdAndDepStatus(run.getPayrollRunId(), DepStatus.SCHEDULED);
        int promoted = 0;
        for (RetirementPensionDeposits s : scheduled) {
            s.markCompleted(LocalDateTime.now());
            affectedEmpIds.add(s.getEmployee().getEmpId());
            promoted++;
        }

        // 2. SCHEDULED 가 없는 DC 사원에 대해서는 기존 흐름 그대로 (자동 산정이 누락됐거나 스킵된 사원 보정)
        List<PayrollDetails> details = payrollDetailsRepository.findByPayrollRuns(run);
        Map<Long, List<PayrollDetails>> byEmp = details.stream()
                .collect(Collectors.groupingBy(d -> d.getEmployee().getEmpId()));

        int created = 0;
        for (Map.Entry<Long, List<PayrollDetails>> entry : byEmp.entrySet()) {
            Employee emp = entry.getValue().get(0).getEmployee();
            if (emp.getRetirementType() != RetirementType.DC) continue;
            if (retirementPensionDepositsRepository.existsByPayrollRun_PayrollRunIdAndEmployee_EmpId(
                    run.getPayrollRunId(), emp.getEmpId())) continue;

            long baseAmount = entry.getValue().stream()
                    .filter(d -> d.getPayItemType() == PayItemType.PAYMENT)
                    .mapToLong(PayrollDetails::getAmount)
                    .sum();
            long depositAmount = baseAmount / 12;

            RetirementPensionDeposits dep = RetirementPensionDeposits.builder()
                    .employee(emp)
                    .payYearMonth(payYearMonth)
                    .baseAmount(baseAmount)
                    .depositAmount(depositAmount)
                    .depositDate(LocalDateTime.now())
                    .depStatus(DepStatus.COMPLETED)
                    .company(run.getCompany())
                    .payrollRun(run)
                    .build();
            retirementPensionDepositsRepository.save(dep);
            affectedEmpIds.add(emp.getEmpId());
            created++;
        }

        // 3. 캐시 일괄 무효화
        for (Long empId : affectedEmpIds) {                               // ← 블록 추가
            mySalaryCacheService.evictPensionCache(companyId, empId);
            mySalaryCacheService.evictSeveranceEstimateCache(companyId, empId);
        }

        log.info("[PensionDeposit] 일괄 적립 처리 - runId={}, promoted={}, created={}, evicted={}",
                run.getPayrollRunId(), promoted, created, affectedEmpIds.size());
        return promoted + created;
    }


    private long calcMonthsBetween(String fromYm, String toYm) {
        if (fromYm == null || toYm == null) return 1;
        java.time.YearMonth from = java.time.YearMonth.parse(fromYm);
        java.time.YearMonth to = java.time.YearMonth.parse(toYm);
        return java.time.temporal.ChronoUnit.MONTHS.between(from, to) + 1;
    }

    private PensionDepositResDto toRes(RetirementPensionDeposits d, Employee emp) {
        return PensionDepositResDto.builder()
                .depId(d.getDepId())
                .empId(d.getEmployee().getEmpId())                                            // ✅
                .empName(emp.getEmpName())
                .deptName(emp.getDept() != null ? emp.getDept().getDeptName() : null)
                .payYearMonth(d.getPayYearMonth())
                .baseAmount(d.getBaseAmount())
                .depositAmount(d.getDepositAmount())
                .depStatus(d.getDepStatus().name())
                .depositDate(d.getDepositDate())
                .payrollRunId(d.getPayrollRun() != null ? d.getPayrollRun().getPayrollRunId() : null)   // ✅
                .isManual(d.getIsManual())
                .build();
    }


    private void writeHeaderRow(Sheet sheet, String[] headers, CellStyle style) {
        Row r = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = r.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(style);
        }
    }

    private static class EmpAggregate {
        final Employee emp;
        long totalBase = 0L;
        long totalDeposit = 0L;
        int monthCount = 0;
        EmpAggregate(Employee emp) { this.emp = emp; }
    }
}


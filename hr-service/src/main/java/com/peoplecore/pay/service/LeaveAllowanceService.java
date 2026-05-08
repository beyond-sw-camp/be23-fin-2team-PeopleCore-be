package com.peoplecore.pay.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.*;
import com.peoplecore.pay.dtos.ApplyResultDto;
import com.peoplecore.pay.dtos.LeaveAllowanceResDto;
import com.peoplecore.pay.dtos.LeaveAllowanceSummaryResDto;
import com.peoplecore.pay.dtos.LeavePolicyTypeResDto;
import com.peoplecore.pay.enums.*;
import com.peoplecore.pay.repository.*;
import com.peoplecore.resign.domain.Resign;
import com.peoplecore.resign.domain.RetireStatus;
import com.peoplecore.resign.repository.ResignRepository;
import com.peoplecore.salarycontract.domain.SalaryContract;
import com.peoplecore.salarycontract.repository.SalaryContractRepository;
import com.peoplecore.vacation.entity.VacationBalance;
import com.peoplecore.vacation.entity.VacationPolicy;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationBalanceRepository;
import com.peoplecore.vacation.repository.VacationPolicyRepository;
import com.peoplecore.vacation.repository.VacationPromotionNoticeRepository;
import com.peoplecore.vacation.repository.VacationTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Slf4j
@Service
@Transactional(readOnly = true)
public class LeaveAllowanceService {

    private final CompanyRepository companyRepository;
    private final PayItemsRepository payItemsRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveAllowanceRepository leaveAllowanceRepository;
    private final SalaryContractRepository salaryContractRepository;
    private final VacationBalanceRepository vacationBalanceRepository;     /* ← Remainder → Balance */
    private final VacationTypeRepository vacationTypeRepository;           /* ← 신규 (연차 typeId 조회용) */
    private final PayrollDetailsRepository payrollDetailsRepository;
    private final PayrollRunsRepository payrollRunsRepository;
    private final VacationPolicyRepository vacationPolicyRepository;
    private final VacationPromotionNoticeRepository vacationPromotionNoticeRepository;  // 촉진 통지 이력 (면제 판정용)
    private final PayrollEmpStatusRepository payrollEmpStatusRepository;
    private final ResignRepository resignRepository;
    private final PaySettingsRepository paySettingsRepository;


    @Autowired
    public LeaveAllowanceService(CompanyRepository companyRepository, PayItemsRepository payItemsRepository, EmployeeRepository employeeRepository, LeaveAllowanceRepository leaveAllowanceRepository, SalaryContractRepository salaryContractRepository, VacationBalanceRepository vacationBalanceRepository, VacationTypeRepository vacationTypeRepository, PayrollDetailsRepository payrollDetailsRepository, PayrollRunsRepository payrollRunsRepository, VacationPolicyRepository vacationPolicyRepository, VacationPromotionNoticeRepository vacationPromotionNoticeRepository, PayrollEmpStatusRepository payrollEmpStatusRepository, ResignRepository resignRepository, PaySettingsRepository paySettingsRepository) {
        this.companyRepository = companyRepository;
        this.payItemsRepository = payItemsRepository;
        this.employeeRepository = employeeRepository;
        this.leaveAllowanceRepository = leaveAllowanceRepository;
        this.salaryContractRepository = salaryContractRepository;
        this.vacationBalanceRepository = vacationBalanceRepository;
        this.vacationTypeRepository = vacationTypeRepository;
        this.payrollDetailsRepository = payrollDetailsRepository;
        this.payrollRunsRepository = payrollRunsRepository;
        this.vacationPolicyRepository = vacationPolicyRepository;
        this.vacationPromotionNoticeRepository = vacationPromotionNoticeRepository;
        this.payrollEmpStatusRepository = payrollEmpStatusRepository;
        this.resignRepository = resignRepository;
        this.paySettingsRepository = paySettingsRepository;
    }


    //    연말 미사용 연차 산정 목록
    @Transactional
    public LeaveAllowanceSummaryResDto getFiscalYearList(UUID companyId, Integer year) {
        return buildSummary(companyId, year, AllowanceType.FISCAL_YEAR);
    }

    //    퇴직자 연차 정산 목록
    @Transactional
    public LeaveAllowanceSummaryResDto getResignedList(UUID companyId, Integer year) {
        return buildSummary(companyId, year, AllowanceType.RESIGNED);
    }

    ///    수당 산정 (선택된 대상자)
    @Transactional
    public void calculate(UUID companyId, Integer year, AllowanceType type, List<Long> empIds) {

        Company company = companyRepository.findById(companyId).orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

///        법정수당 항목 존재 확인
        payItemsRepository.findByCompany_CompanyIdAndIsLegalTrueAndLegalCalcType(companyId, LegalCalcType.LEAVE).orElseThrow(() -> new CustomException(ErrorCode.LEAVE_ALLOWANCE_NOT_ENABLED));

        for (Long empId : empIds) {
            Employee emp = employeeRepository.findById(empId).orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

            // PENDING 은 산정 진행, CALCULATED/APPLIED/EXEMPTED 는 스킵
            LeaveAllowance existing = leaveAllowanceRepository.findFirstByCompany_CompanyIdAndEmployee_EmpIdAndYearAndAllowanceType(companyId, empId, year, type).orElse(null);

            if (existing != null && existing.getStatus() != AllowanceStatus.PENDING) {
                continue;
            }

            // 통상임금 기준일 결정
            LocalDate basisDate = resolveBasisDate(type, year, emp);
//            통상임금(월) = 연봉 / 12
            SalaryContract contract = salaryContractRepository
                    .findActiveContractsByEmpIds(companyId, List.of(empId), basisDate, basisDate)
                    .stream().findFirst()
                    .orElse(null);
            if (contract == null) continue;

            long monthlySalary = contract.getTotalAmount().divide(BigDecimal.valueOf(12), 0, RoundingMode.FLOOR).longValue();

//            일 통상임금 = 통상임금(월) / 209 * 8
            long dailyWage = Math.round((double) monthlySalary / 209 * 8);

//            연차 잔여 조회
            BigDecimal totalDays;
            BigDecimal usedDays;
            BigDecimal unusedDays;

            /* ANNUAL 유형 ID 1회 조회 — 회사당 1건 보장 (회사 생성 시 자동 INSERT) */
            VacationType annualType = vacationTypeRepository
                    .findByCompanyIdAndTypeCode(companyId, VacationType.CODE_ANNUAL)
                    .orElseThrow(() -> new CustomException(ErrorCode.VACATION_TYPE_NOT_FOUND));


            if (type == AllowanceType.ANNIVERSARY) {
                /* 입사일 기준 - 입사 기준일 도래시 만료되는 기간 = year-1 */
                int lookupYear = year - 1;
                VacationBalance balance = vacationBalanceRepository
                        .findForAllowance(companyId, empId, annualType.getTypeId(), lookupYear)
                        .orElse(null);

                totalDays = balance != null ? balance.getTotalDays() : BigDecimal.ZERO;
                usedDays = balance != null ? balance.getUsedDays() : BigDecimal.ZERO;
                unusedDays = totalDays.subtract(usedDays);
            } else {
                /* 회계년도 기준 / 퇴직자 - 해당 연도 잔여 */
                VacationBalance balance = vacationBalanceRepository
                        .findForAllowance(companyId, empId, annualType.getTypeId(), year)
                        .orElse(null);

                totalDays = balance != null ? balance.getTotalDays() : BigDecimal.ZERO;
                usedDays = balance != null ? balance.getUsedDays() : BigDecimal.ZERO;
                unusedDays = totalDays.subtract(usedDays);
            }

//            미사용인 0이하면 스킵
            if (unusedDays.compareTo(BigDecimal.ZERO) <= 0) continue;

            //근기법 제61조 - 촉진 통지 1차+2차 모두 완료 시 미사용 수당 면제
            long noticeCnt = vacationPromotionNoticeRepository.countNoticeStages(companyId, empId, year);
            if (noticeCnt >= 2) {
                log.info("[LeaveAllowance] 촉진 통지 완료 - 수당 면제. empId={}, year={}", empId, year);
                LeaveAllowance exempted = existing != null
                        ? existing
                        : LeaveAllowance.builder()
                          .company(company)
                          .employee(emp)
                          .year(year)
                          .allowanceType(type)
                          .resignDate(type == AllowanceType.RESIGNED ? emp.getEmpResignDate() : null)
                          .status(AllowanceStatus.EXEMPTED)
                          .build();
                exempted.calculate(monthlySalary, dailyWage, totalDays, usedDays, unusedDays, 0L);
                leaveAllowanceRepository.save(exempted);
                continue;
            }

//            산정금액 = 미사용일수 * 일 통상임금
            long amount = unusedDays.multiply(BigDecimal.valueOf(dailyWage)).longValue();

            LeaveAllowance allowance = existing != null
                    ? existing  // 기존 PENDING 재사용 (dirty checking으로 update)
                    : LeaveAllowance.builder()
                      .company(company)
                      .employee(emp)
                      .year(year)
                      .allowanceType(type)
                      .resignDate(type == AllowanceType.RESIGNED ? emp.getEmpResignDate() : null)
                      .status(AllowanceStatus.PENDING)
                      .build();

            allowance.calculate(monthlySalary, dailyWage, totalDays, usedDays, unusedDays, amount);
            leaveAllowanceRepository.save(allowance);
        }
    }

    /**
     * 퇴직 처리 시 호출 — 후보 INSERT + 즉시 산정 (CALCULATED 까지)
     * 통상임금 정보 부족 등으로 산정 실패하면 PENDING 으로 남김.
     */
    @Transactional
    public void createResignedAndCalculate(UUID companyId, Long empId, LocalDate resignDate) {
        int year = resignDate.getYear();

        // 중복 방지
        if (leaveAllowanceRepository.existsByCompany_CompanyIdAndEmployee_EmpIdAndYearAndAllowanceType(
                companyId, empId, year, AllowanceType.RESIGNED)) {
            return;
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMPANY_NOT_FOUND));
        Employee emp = employeeRepository.findById(empId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        LeaveAllowance allowance = LeaveAllowance.builder()
                .company(company)
                .employee(emp)
                .year(year)
                .allowanceType(AllowanceType.RESIGNED)
                .resignDate(resignDate)
                .status(AllowanceStatus.PENDING)
                .build();
        leaveAllowanceRepository.save(allowance);

        // 자동 산정 (실패해도 PENDING 으로 남기고 예외 무시)
        try {
            calculate(companyId, year, AllowanceType.RESIGNED, List.of(empId));
        } catch (Exception e) {
            log.warn("[연차수당 자동산정 실패] empId={}, type=RESIGNED, msg={}", empId, e.getMessage());
        }
    }

    ///    급여대장 반영(선택된 대상자)
    @Transactional
    public ApplyResultDto applyToPayroll(UUID companyId, List<Long> allowanceIds) {

        int appliedCount = 0;
        int skippedCount = 0;
        List<Long> skippedAllowanceIds = new ArrayList<>();

        List<LeaveAllowance> allowances = leaveAllowanceRepository.findByAllowanceIdInAndCompany_CompanyId(allowanceIds, companyId);

//        법정수당(LEAVE) 항목
        PayItems leavePayItem = payItemsRepository.findByCompany_CompanyIdAndIsLegalTrueAndLegalCalcType(companyId, LegalCalcType.LEAVE).orElseThrow(() -> new CustomException(ErrorCode.LEAVE_ALLOWANCE_NOT_ENABLED));

        for (LeaveAllowance la : allowances) {
            if (la.getStatus() == AllowanceStatus.APPLIED) continue;
            // CALCULATED: 신규 반영 / SKIPPED: 이전 시도가 잠금으로 실패 → 재시도 허용
            if (la.getStatus() != AllowanceStatus.CALCULATED && la.getStatus() != AllowanceStatus.SKIPPED) {
                throw new CustomException(ErrorCode.LEAVE_ALLOWANCE_NOT_CALCULATED);
            }

//            반영 대상 월 결정
            String targetMonth = resolveTargetMonth(la);

//            해당 월 급여대장 조회(없으면 에러)
            PayrollRuns run = payrollRunsRepository.findByCompany_CompanyIdAndPayYearMonth(companyId, targetMonth).orElseThrow(() -> new CustomException(ErrorCode.PAYROLL_NOT_FOUND));

// run-level 은 PAID 만 차단
            if (run.getPayrollStatus() == PayrollStatus.PAID) {
                skippedCount++;
                skippedAllowanceIds.add(la.getAllowanceId());
                la.markSkipped();   // 카운트 잔존 방지 — CALCULATED → SKIPPED
                log.warn("[연차수당 반영 skip] runId={}, 이미 지급완료", run.getPayrollRunId());
                continue;
            }

// 사원별 PayrollEmpStatus 검증
            PayrollEmpStatus pes = payrollEmpStatusRepository
                    .findByPayrollRuns_PayrollRunIdAndEmployee_EmpId(
                            run.getPayrollRunId(), la.getEmployee().getEmpId())
                    .orElse(null);

            if (pes != null && pes.getStatus() != PayrollEmpStatusType.CALCULATING) {
                skippedCount++;
                skippedAllowanceIds.add(la.getAllowanceId());
                la.markSkipped();   // 카운트 잔존 방지 — CALCULATED → SKIPPED
                log.warn("[연차수당 반영 skip] empId={} 사원 상태={}, runId={}",
                        la.getEmployee().getEmpId(), pes.getStatus(), run.getPayrollRunId());
                continue;
            }

//            PayrollDetails  추가
            PayrollDetails details = PayrollDetails.builder()
                    .payrollRuns(run)
                    .employee(la.getEmployee())
                    .payItems(leavePayItem)
                    .payItemName(leavePayItem.getPayItemName())
                    .payItemType(PayItemType.PAYMENT)
                    .amount(la.getAllowanceAmount())
                    .memo("연차수당 (" + la.getUnusedLeaveDays() + " 일)")
                    .company(la.getCompany())
                    .build();
            payrollDetailsRepository.save(details);

            recalculateTotals(run);

            la.markApplied(run.getPayrollRunId(), targetMonth);

            // 반영 성공 카운트
            appliedCount++;
        }

        return ApplyResultDto.builder()
                .appliedCount(appliedCount)
                .skippedCount(skippedCount)
                .skippedAllowanceIds(skippedAllowanceIds)
                .build();
        }


///    회사 연차정책 타입 조회
    public LeavePolicyTypeResDto getPolicyType(UUID companyId){
        VacationPolicy policy = vacationPolicyRepository.findByCompanyId(companyId).orElseThrow(()-> new CustomException(ErrorCode.VACATION_POLICY_NOT_FOUND));

        return LeavePolicyTypeResDto.builder()
                .policyBaseType(policy.getPolicyBaseType().name())
                .fiscalYearStart(policy.getPolicyFiscalYearStart())
                .build();
    }

///    입사일 기준 연차수당 목록
    public LeaveAllowanceSummaryResDto getAnniversaryList(UUID companyId, String yearMonth){
        int targetYear = Integer.parseInt(yearMonth.substring(0,4));
        int targetMonth = Integer.parseInt(yearMonth.substring(5,7));

        List<LeaveAllowance> list = leaveAllowanceRepository.findAllByCompanyAndYearAndType(companyId, targetYear, AllowanceType.ANNIVERSARY);

//        해당 월 입사일 대상자만 필터
        List<LeaveAllowance> filtered = list.stream()
                .filter(la-> la.getEmployee().getEmpHireDate() != null
                && la.getEmployee().getEmpHireDate().getMonthValue() == targetMonth)
                .toList();

//        최초 조회시 PENDING 레코드 자동 생성
        if(filtered.isEmpty()) {
            createAnniversaryPendingRecords(companyId,targetYear, targetMonth);
            list = leaveAllowanceRepository.findAllByCompanyAndYearAndType(companyId, targetYear,AllowanceType.ANNIVERSARY);
            filtered = list.stream()
                    .filter(la-> la.getEmployee().getEmpHireDate() != null && la.getEmployee().getEmpHireDate().getMonthValue() == targetMonth)
                    .toList();
        }
        List<LeaveAllowanceResDto> employees = filtered.stream()
                .map(LeaveAllowanceResDto::fromEntity)
                .toList();

        long calculatedCount = filtered.stream()
                .filter(la -> la.getStatus() == AllowanceStatus.CALCULATED
                        || la.getStatus() == AllowanceStatus.APPLIED)
                .count();

        long appliedCount = filtered.stream()
                .filter(la -> la.getStatus() == AllowanceStatus.APPLIED)
                .count();

        long totalAmount = filtered.stream()
                .filter(la -> la.getAllowanceAmount() != null)
                .mapToLong(LeaveAllowance::getAllowanceAmount)
                .sum();

        return LeaveAllowanceSummaryResDto.builder()
                .totalTarget(filtered.size())
                .calculatedCount((int) calculatedCount)
                .appliedCount((int) appliedCount)
                .totalAllowanceAmount(totalAmount)
                .employees(employees)
                .build();
    }

    /* 반영 대상 월 결정 - 회사 급여 지급일/정책 기준으로 자동 결정.
     * 노동법: 만료 직후 가장 빠른 정기 임금일이 속한 월.
     * - CURRENT(당월): 5월 근로 → 5월 25일 지급
     * - NEXT(익월): 5월 근로 → 6월 25일 지급
     */
    private String resolveTargetMonth(LeaveAllowance la) {
        CompanyPaySettings settings = paySettingsRepository
                .findByCompany_CompanyId(la.getCompany().getCompanyId())
                .orElse(null);
        return resolveTargetMonth(la, settings);
    }

    private String resolveTargetMonth(LeaveAllowance la, CompanyPaySettings settings) {
        LocalDate expiration = resolveExpirationDate(la);       // 만료일
        LocalDate dueDate = expiration.plusDays(1);   // 산정 가능 시점

        // 회사 급여 설정 (없으면 안전 기본값: 매월 25일, 당월 지급)
        int payDay = (settings == null) ? 25
                : Boolean.TRUE.equals(settings.getSalaryPayLastDay()) ? 31
                  : settings.getSalaryPayDay();
        PayMonth payMonth = (settings == null || settings.getSalaryPayMonth() == null)
                ? PayMonth.CURRENT
                : settings.getSalaryPayMonth();

        // 만료 다음의 가장 빠른 정기 임금일이 속한 yearMonth (근로월 기준)
        YearMonth workMonth = YearMonth.from(dueDate);
        int dayCap = Math.min(payDay, workMonth.lengthOfMonth());
        LocalDate firstPaydayAfter = workMonth.atDay(dayCap);
        if (dueDate.isAfter(firstPaydayAfter)) {
            workMonth = workMonth.plusMonths(1);   // 그 달 급여일 지났으면 다음 근로월
        }

        // NEXT(익월 지급) 정책이면 한 달 더 미룸 (5월 근로 → 6월 25일 지급)
        YearMonth payTargetMonth = (payMonth == PayMonth.NEXT) ? workMonth.plusMonths(1) : workMonth;

        return payTargetMonth.toString();   // "2026-05" / "2027-01"
    }

//    "검토 대기 N명" — 지정한 yearMonth 급여대장에 반영될 CALCULATED 건의 distinct 사원수
    public long countPendingReviewForMonth(UUID companyId, String yearMonth) {
        List<LeaveAllowance> candidates = leaveAllowanceRepository
                .findPendingReviewCandidates(companyId, AllowanceStatus.CALCULATED,
                        List.of(AllowanceType.ANNIVERSARY, AllowanceType.RESIGNED));
        if (candidates.isEmpty()) return 0L;

        CompanyPaySettings settings = paySettingsRepository
                .findByCompany_CompanyId(companyId)
                .orElse(null);

        return candidates.stream()
                .filter(la -> {
                    try {
                        return yearMonth.equals(resolveTargetMonth(la, settings));
                    } catch (Exception e) {
                        log.warn("[검토 대기 카운트 skip] allowanceId={}, msg={}", la.getAllowanceId(), e.getMessage());
                        return false;
                    }
                })
                .map(la -> la.getEmployee().getEmpId())
                .distinct()
                .count();
    }


    /* 만료일 결정 - FISCAL: 그 해 12-31, ANNIVERSARY: 그 해 입사기념일, RESIGNED: 퇴직일 */
    private LocalDate resolveExpirationDate(LeaveAllowance la) {
        if (la.getAllowanceType() == AllowanceType.RESIGNED) {
            if (la.getResignDate() == null) {
                throw new CustomException(ErrorCode.LEAVE_ALLOWANCE_NO_RESIGN_DATE);
            }
            return la.getResignDate();
        }
        if (la.getAllowanceType() == AllowanceType.ANNIVERSARY) {
            LocalDate hire = la.getEmployee().getEmpHireDate();
            if (hire == null) {
                throw new CustomException(ErrorCode.EMPLOYEE_HIRE_DATE_NOT_FOUND);
            }
            try {
                return LocalDate.of(la.getYear(), hire.getMonthValue(), hire.getDayOfMonth());
            } catch (DateTimeException e) {
                return LocalDate.of(la.getYear(), hire.getMonthValue(), 28);
            }
        }
        // FISCAL_YEAR
        return LocalDate.of(la.getYear(), 12, 31);
    }


//  상단 요약 dto
    private LeaveAllowanceSummaryResDto buildSummary(UUID companyId, Integer year, AllowanceType type){
        // 신규 대상자 자동 포함 - ANNIVERSARY 는 별도 트리거(createAnniversaryPendingRecords)
        if (type != AllowanceType.ANNIVERSARY) {
            createPendingRecords(companyId, year, type);
        }
        List<LeaveAllowance> list = leaveAllowanceRepository
                .findAllByCompanyAndYearAndType(companyId, year, type);

        List<LeaveAllowanceResDto> employees = list.stream().map(LeaveAllowanceResDto::fromEntity).toList();

//      산정완료 + 급여반영 된 사원 수
        long calculatedCount = list.stream().filter(
                la-> la.getStatus() == AllowanceStatus.CALCULATED || la.getStatus() == AllowanceStatus.APPLIED)
                .count();

//      급여반영까지 완료된 사원 수
        long appliedCount = list.stream().filter(
la -> la.getStatus() == AllowanceStatus.APPLIED)
                .count();

//      산정금액 합계
        long totalAmount = list.stream().filter(
                la-> la.getAllowanceAmount() != null)
                .mapToLong(LeaveAllowance::getAllowanceAmount)
                .sum();

        return LeaveAllowanceSummaryResDto.builder()
                .totalTarget(list.size())
                .calculatedCount((int) calculatedCount)
                .appliedCount((int) appliedCount)
                .totalAllowanceAmount(totalAmount)
                .employees(employees)
                .build();
    }


//    대상 사원 PENDING 레코드 자동 생성(최초 시)
    @Transactional
    public void createPendingRecords(UUID companyId, Integer year, AllowanceType type){

        Company company = companyRepository.findById(companyId).orElseThrow(()-> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

        List<Employee> targets;

        if (type == AllowanceType.FISCAL_YEAR){
        // 회계연도(12-31) 미도래 시 PENDING 생성 안 함
            if (LocalDate.now().isBefore(LocalDate.of(year, 12, 31).plusDays(1))) return;
//      재직/휴직 사원(퇴직자 제외)
            targets = employeeRepository.findByCompany_CompanyIdAndEmpStatusInAndDeleteAtIsNull(companyId, List.of(EmpStatus.ACTIVE, EmpStatus.ON_LEAVE));
        } else {
            // 해당연도 퇴직(완료) + 퇴직예정(CONFIRMED) 사원
            targets = collectResignedAndPendingTargets(companyId, year);
        }

        for (Employee emp : targets){
            if(leaveAllowanceRepository.existsByCompany_CompanyIdAndEmployee_EmpIdAndYearAndAllowanceType(companyId, emp.getEmpId(),year, type)){
                continue;
            }
            LocalDate effectiveResignDate = resolveEffectiveResignDate(companyId, emp, type);
            LeaveAllowance allowance = LeaveAllowance.builder()
                    .company(company)
                    .employee(emp)
                    .year(year)
                    .allowanceType(type)
                    .resignDate(type == AllowanceType.RESIGNED ? effectiveResignDate : null)
                    .status(AllowanceStatus.PENDING)
                    .build();
            leaveAllowanceRepository.save(allowance);
        }
    }

//   이번달이 입사일기준 대상사원 PENDING 레코드 생성 (입사기념일 도래자만)
    @Transactional
    public void createAnniversaryPendingRecords(UUID companyId, int year, int month){
        Company company = companyRepository.findById(companyId).orElseThrow(()-> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

        LocalDate today = LocalDate.now();

//        재직/휴직 사원중 입사월이 대상 월이고, 그 해 입사기념일이 도래(당일 포함)한 사원만
        List<Employee> targets = employeeRepository
                .findByCompany_CompanyIdAndEmpStatusInAndDeleteAtIsNull(
                        companyId, List.of(EmpStatus.ACTIVE, EmpStatus.ON_LEAVE))
                .stream()
                .filter(e -> e.getEmpHireDate() != null
                        && e.getEmpHireDate().getMonthValue() == month
                        && !resolveAnniversaryDateInYear(e.getEmpHireDate(), year)
                        .isAfter(today))
                .toList();

        for (Employee emp : targets) {
            if (leaveAllowanceRepository.existsByCompany_CompanyIdAndEmployee_EmpIdAndYearAndAllowanceType(
                    companyId, emp.getEmpId(), year, AllowanceType.ANNIVERSARY)) {
                continue;
            }


            LeaveAllowance allowance = LeaveAllowance.builder()
                    .company(company)
                    .employee(emp)
                    .year(year)
                    .allowanceType(AllowanceType.ANNIVERSARY)
                    .status(AllowanceStatus.PENDING)
                    .build();

            leaveAllowanceRepository.save(allowance);
        }
    }

//    급여대장 합계 재계산
     private void recalculateTotals(PayrollRuns run) {
        List<PayrollDetails> allDetails = payrollDetailsRepository.findByPayrollRuns(run);

        long totalPay = allDetails.stream()
                .filter(d-> d.getPayItemType()  == PayItemType.PAYMENT)
                .mapToLong(PayrollDetails::getAmount).sum();

        long totalDeduction = allDetails.stream()
                .filter(d-> d.getPayItemType() == PayItemType.DEDUCTION)
                .mapToLong(PayrollDetails::getAmount).sum();

        int empCount = (int) allDetails.stream()
                .map(d-> d.getEmployee().getEmpId())
                .distinct().count();

        run.updateTotals(empCount, totalPay, totalDeduction, totalPay - totalDeduction);
     }


    /**
     * 연차수당 통상임금 기준일 결정
     * - RESIGNED: 퇴직일
     * - ANNIVERSARY: 그 해의 입사기념일
     * - FISCAL_YEAR: 그 해 12-31
     */
    private LocalDate resolveBasisDate(AllowanceType type, int year, Employee emp) {
        if (type == AllowanceType.RESIGNED) {
            if (emp.getEmpResignDate() != null) return emp.getEmpResignDate();
            // 퇴직예정 사원 - Resign.resignDate fallback
            UUID companyId = emp.getCompany().getCompanyId();
            return resignRepository.findActiveOrConfirmedByEmpId(companyId, emp.getEmpId())
                    .map(Resign::getResignDate)
                    .orElse(LocalDate.of(year, 12, 31)); // 둘 다 없으면 마지막 fallback
        }
        if (type == AllowanceType.ANNIVERSARY) {
            LocalDate hire = emp.getEmpHireDate();
            if (hire == null) return LocalDate.of(year, 12, 31);
            // 그 year 의 입사 기념일 (윤년 2-29 입사자 보정)
            try {
                return LocalDate.of(year, hire.getMonthValue(), hire.getDayOfMonth());
            } catch (DateTimeException e) {
                return LocalDate.of(year, hire.getMonthValue(), 28);
            }
        }
        // FISCAL_YEAR
        return LocalDate.of(year, 12, 31);
    }


    /**
     * 해당 연도에 퇴직(완료/예정)인 사원 수집.
     * - EmpStatus.RESIGNED: empResignDate.year == year
     * - Resign.retireStatus IN (CONFIRMED): resignDate.year == year, Employee.empStatus 무관
     * 동일 사원 중복 시 RESIGNED 우선.
     */
    private List<Employee> collectResignedAndPendingTargets(UUID companyId, int year) {
        Map<Long, Employee> byEmpId = new LinkedHashMap<>();

        // 1) 이미 퇴직 완료된 사원
        employeeRepository
                .findByCompany_CompanyIdAndEmpStatusAndDeleteAtIsNull(companyId, EmpStatus.RESIGNED)
                .stream()
                .filter(e -> e.getEmpResignDate() != null && e.getEmpResignDate().getYear() == year)
                .forEach(e -> byEmpId.put(e.getEmpId(), e));

        // 2) 퇴직예정 사원 (Resign.retireStatus = CONFIRMED)
        resignRepository
                .findAllByRetireStatusAndIsDeletedFalseAndResignDateBetween(
                        RetireStatus.CONFIRMED,
                        LocalDate.of(year, 1, 1),
                        LocalDate.of(year, 12, 31))
                .stream()
                .filter(r -> r.getEmployee().getCompany().getCompanyId().equals(companyId))
                .filter(r -> Boolean.FALSE.equals(r.getEmployee().getDeleteAt() != null)) // 삭제된 사원 제외
                .forEach(r -> byEmpId.putIfAbsent(r.getEmployee().getEmpId(), r.getEmployee()));

        return new ArrayList<>(byEmpId.values());
    }


    private LocalDate resolveAnniversaryDateInYear(LocalDate hireDate, int year) {
        try {
            return LocalDate.of(year, hireDate.getMonthValue(), hireDate.getDayOfMonth());
        } catch (DateTimeException e) {
            return LocalDate.of(year, hireDate.getMonthValue(), 28);
        }
    }


    /**
     * 퇴직일 fallback - empResignDate 없으면 Resign.resignDate 로 대체.
     * SeveranceService.java:113-120 와 동일 패턴.
     */
    private LocalDate resolveEffectiveResignDate(UUID companyId, Employee emp, AllowanceType type) {
        if (type != AllowanceType.RESIGNED) return null;
        if (emp.getEmpResignDate() != null) return emp.getEmpResignDate();
        return resignRepository
                .findActiveOrConfirmedByEmpId(companyId, emp.getEmpId())
                .map(Resign::getResignDate)
                .orElse(null);
    }
}

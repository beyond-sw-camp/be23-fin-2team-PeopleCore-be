package com.peoplecore.pay.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.LeaveAllowance;
import com.peoplecore.pay.domain.PayItems;
import com.peoplecore.pay.domain.PayrollDetails;
import com.peoplecore.pay.domain.PayrollRuns;
import com.peoplecore.pay.dtos.LeaveAllowanceResDto;
import com.peoplecore.pay.dtos.LeaveAllowanceSummaryResDto;
import com.peoplecore.pay.dtos.LeavePolicyTypeResDto;
import com.peoplecore.pay.enums.*;
import com.peoplecore.pay.repository.LeaveAllowanceRepository;
import com.peoplecore.pay.repository.PayItemsRepository;
import com.peoplecore.pay.repository.PayrollDetailsRepository;
import com.peoplecore.pay.repository.PayrollRunsRepository;
import com.peoplecore.salarycontract.domain.ContractStatus;
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
import java.util.List;
import java.util.UUID;

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

    @Autowired
    public LeaveAllowanceService(CompanyRepository companyRepository, PayItemsRepository payItemsRepository, EmployeeRepository employeeRepository, LeaveAllowanceRepository leaveAllowanceRepository, SalaryContractRepository salaryContractRepository, VacationBalanceRepository vacationBalanceRepository, VacationTypeRepository vacationTypeRepository, PayrollDetailsRepository payrollDetailsRepository, PayrollRunsRepository payrollRunsRepository, VacationPolicyRepository vacationPolicyRepository, VacationPromotionNoticeRepository vacationPromotionNoticeRepository) {
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
    }


    //    연말 미사용 연차 산정 목록
    public LeaveAllowanceSummaryResDto getFiscalYearList(UUID companyId, Integer year) {
        return buildSummary(companyId, year, AllowanceType.FISCAL_YEAR);
    }

    //    퇴직자 연차 정산 목록
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

            // 이미 산정된 경우 스킵
            if (leaveAllowanceRepository.existsByCompany_CompanyIdAndEmployee_EmpIdAndYearAndAllowanceType(companyId, empId, year, type)) {
                continue;
            }

//            통상임금(월) = 연봉 / 12
            SalaryContract contract = salaryContractRepository.findTopByEmployee_EmpIdAndStatusOrderByContractYearDesc(empId, ContractStatus.SIGNED).orElse(null);
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
                LeaveAllowance exempted = LeaveAllowance.builder()
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

            LeaveAllowance allowance = LeaveAllowance.builder()
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

    ///    급여대장 반영(선택된 대상자)
    @Transactional
    public void applyToPayroll(UUID companyId, List<Long> allowanceIds) {

        List<LeaveAllowance> allowances = leaveAllowanceRepository.findByAllowanceIdInAndCompany_CompanyId(allowanceIds, companyId);

//        법정수당(LEAVE) 항목
        PayItems leavePayItem = payItemsRepository.findByCompany_CompanyIdAndIsLegalTrueAndLegalCalcType(companyId, LegalCalcType.LEAVE).orElseThrow(() -> new CustomException(ErrorCode.LEAVE_ALLOWANCE_NOT_ENABLED));

        for (LeaveAllowance la : allowances) {
            if (la.getStatus() == AllowanceStatus.APPLIED) continue;
            if (la.getStatus() != AllowanceStatus.CALCULATED) {
                throw new CustomException(ErrorCode.LEAVE_ALLOWANCE_NOT_CALCULATED);
            }

//            반영 대상 월 결정
            String targetMonth = resolveTargetMonth(la);

//            해당 월 급여대장 조회(없으면 에러)
            PayrollRuns run = payrollRunsRepository.findByCompany_CompanyIdAndPayYearMonth(companyId, targetMonth).orElseThrow(() -> new CustomException(ErrorCode.PAYROLL_NOT_FOUND));

            if (run.getPayrollStatus() != PayrollStatus.CALCULATING) {
                throw new CustomException(ErrorCode.PAYROLL_STATUS_INVALID);
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

        }
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


    ///    반영대상 월 결정
    private String resolveTargetMonth(LeaveAllowance la) {
        if (la.getAllowanceType() == AllowanceType.FISCAL_YEAR) {
//           회계년도 : 연말 미사용 -> 해당 연도 12월
            return la.getYear() + "-12";
        } else if (la.getAllowanceType() == AllowanceType.ANNIVERSARY) {
//           입사일 기준 -> 입사 기념일 월
            if (la.getEmployee().getEmpHireDate() == null){
                throw new CustomException(ErrorCode.EMPLOYEE_HIRE_DATE_NOT_FOUND);
            }
            int month = la.getEmployee().getEmpHireDate().getMonthValue();
            return la.getYear() + "-" + String.format("%02d",month);
        } else {
//           퇴직자 -> 퇴직월
            if (la.getResignDate() == null) {
                throw new CustomException(ErrorCode.LEAVE_ALLOWANCE_NO_RESIGN_DATE);
            }
            return la.getResignDate().getYear() + "-" +
                    String.format("%02d", la.getResignDate().getMonthValue());
        }
    }


//  상단 요약 dto
    private LeaveAllowanceSummaryResDto buildSummary(UUID companyId, Integer year, AllowanceType type){
        List<LeaveAllowance> list = leaveAllowanceRepository.findAllByCompanyAndYearAndType(companyId, year, type);

//        아직 LeaveAllowance 엔티티가 없는 대상자
//        최초 조회시 대상 사원을 PENDING 상태로 자동 생성
        if(list.isEmpty()){
            createPendingRecords(companyId, year, type);
            list = leaveAllowanceRepository.findAllByCompanyAndYearAndType(companyId, year, type);
        }

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
//        재직/휴직 사원(퇴직자 제외)
            targets = employeeRepository.findByCompany_CompanyIdAndEmpStatusInAndDeleteAtIsNull(companyId, List.of(EmpStatus.ACTIVE, EmpStatus.ON_LEAVE));
        } else {
//            해당연도 퇴직 사원
            targets = employeeRepository.findByCompany_CompanyIdAndEmpStatusAndDeleteAtIsNull(companyId, EmpStatus.RESIGNED)
                    .stream()
                    .filter(e-> e.getEmpResignDate() != null && e.getEmpResignDate().getYear() == year)
                    .toList();
        }

        for (Employee emp : targets){
            if(leaveAllowanceRepository.existsByCompany_CompanyIdAndEmployee_EmpIdAndYearAndAllowanceType(companyId, emp.getEmpId(),year, type)){
                continue;
            }
            LeaveAllowance allowance = LeaveAllowance.builder()
                    .company(company)
                    .employee(emp)
                    .year(year)
                    .allowanceType(type)
                    .resignDate(type == AllowanceType.RESIGNED ? emp.getEmpResignDate() : null)
                    .status(AllowanceStatus.PENDING)
                    .build();
            leaveAllowanceRepository.save(allowance);
        }
    }

//    입사일기준 대상사원 PENDING 레코드 생성
    @Transactional
    public void createAnniversaryPendingRecords(UUID companyId, int year, int month){
        Company company = companyRepository.findById(companyId).orElseThrow(()-> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

//        재직/휴직 사원중 입사월이 대상 월인 사원
        List<Employee> targets = employeeRepository.findByCompany_CompanyIdAndEmpStatusInAndDeleteAtIsNull(companyId, List.of(EmpStatus.ACTIVE, EmpStatus.ON_LEAVE  ))
                .stream()
                .filter(e-> e.getEmpHireDate() != null && e.getEmpHireDate().getMonthValue() == month)
                .toList();

        for(Employee emp : targets){
            if(leaveAllowanceRepository.existsByCompany_CompanyIdAndEmployee_EmpIdAndYearAndAllowanceType(companyId, emp.getEmpId(), year, AllowanceType.ANNIVERSARY)){
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
}

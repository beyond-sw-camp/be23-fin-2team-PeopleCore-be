package com.peoplecore.pay.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.event.SeveranceApprovalResultEvent;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.EmpRetirementAccount;
import com.peoplecore.pay.domain.LeaveAllowance;
import com.peoplecore.pay.domain.RetirementSettings;
import com.peoplecore.pay.domain.SeverancePays;
import com.peoplecore.pay.dtos.*;
import com.peoplecore.pay.enums.*;
import com.peoplecore.pay.repository.*;
import com.peoplecore.pay.tax.RetirementIncomeTaxCalculator;
import com.peoplecore.resign.domain.Resign;
import com.peoplecore.resign.repository.ResignRepository;
import com.peoplecore.vacation.entity.VacationPolicy;
import com.peoplecore.vacation.repository.VacationPolicyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static reactor.netty.http.HttpConnectionLiveness.log;

@Service
@Transactional(readOnly = true)
@Slf4j
public class SeveranceService {

    private final SeverancePaysRepository severancePaysRepository;
    private final CompanyRepository companyRepository;
    private final EmployeeRepository employeeRepository;
    private final EmpRetirementAccountRepository empRetirementAccountRepository;
    private final RetirementSettingsRepository retirementSettingsRepository;
    private final SeverancePaysRepositoryImpl severanceRepositoryImpl;
    private final VacationPolicyRepository vacationPolicyRepository;
    private final LeaveAllowanceRepository leaveAllowanceRepository;
    private final RetirementIncomeTaxCalculator retirementIncomeTaxCalculator;
    private final ResignRepository resignRepository;

    @Autowired
    public SeveranceService(SeverancePaysRepository severanceRepository, CompanyRepository companyRepository, EmployeeRepository employeeRepository, EmpRetirementAccountRepository empRetirementAccountRepository, RetirementSettingsRepository retirementSettingsRepository, SeverancePaysRepositoryImpl severanceRepositoryImpl, VacationPolicyRepository vacationPolicyRepository, LeaveAllowanceRepository leaveAllowanceRepository, RetirementIncomeTaxCalculator retirementIncomeTaxCalculator, SeverancePaysRepository severancePaysRepository, ResignRepository resignRepository) {
        this.companyRepository = companyRepository;
        this.employeeRepository = employeeRepository;
        this.empRetirementAccountRepository = empRetirementAccountRepository;
        this.retirementSettingsRepository = retirementSettingsRepository;
        this.severanceRepositoryImpl = severanceRepositoryImpl;
        this.vacationPolicyRepository = vacationPolicyRepository;
        this.leaveAllowanceRepository = leaveAllowanceRepository;
        this.retirementIncomeTaxCalculator = retirementIncomeTaxCalculator;
        this.severancePaysRepository = severancePaysRepository;
        this.resignRepository = resignRepository;
    }

//    사원 퇴직 이벤트 발생시 퇴직금 자동산정용 메서드
    @Transactional
    public void calculateByEmpId(UUID companyId, Long empId){
        SeveranceCalcReqDto reqDto = SeveranceCalcReqDto.builder().empId(empId).build();
        calculateSeverance(companyId,reqDto);
    }

/// 퇴직금 산정 (퇴직 확정된 사원 대상)
/* 1. 법정 퇴직금 계산
*   1일 평균 임금 = (최근3개월 급여총액 + 상여금가산액 + 연차수당) / 직전3개월 총일수
*   상여금 가산액 = 직전 1년 상여금 * (3/12)
* ** 통상임금 비교 : 평균임금 < 통상임금이면 통상임금을 적용 (근로기준법 제2조) **
*   퇴직금 = 1일 평균 임금 * 30 * (근속일수 / 365)
*
* 2. DC형인 경우
*   기적립금 합산 -> 차액 = 퇴직금 - 기적립금 (차액만 지급)
*
* 3. DB형인 경우
*   법정 퇴직금 전액 지급 (적립은 사업주 부담)
* */
    @Transactional
    public SeveranceDetailResDto calculateSeverance(UUID companyId, SeveranceCalcReqDto reqDto){

        Long empId = reqDto.getEmpId();
        Company company = companyRepository.findById(companyId).orElseThrow(()-> new CustomException(ErrorCode.COMPANY_NOT_FOUND));

        Employee emp = employeeRepository.findByEmpIdAndCompany_CompanyId(empId, companyId).orElseThrow(()-> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

//        이미 산정된 것이 있으면 재산정 (CALCULATING 상태일때만)
        Optional<SeverancePays> existing = severancePaysRepository
                .findByEmployee_EmpIdAndCompany_CompanyId(empId, companyId)
                .stream()
                .filter(s-> s.getSevStatus() == SevStatus.CALCULATING)
                .findFirst();

        LocalDate hireDate = emp.getEmpHireDate();
        LocalDate resignDate = emp.getEmpResignDate();

// Employee.empResignDate 미설정(아직 RESIGNED 상태가 아님) → 활성 Resign 의 퇴직예정일 fallback
// 운영 시나리오: CONFIRMED 상태에서 퇴직금 사전 산정 (직원 사전 통보 + 대장작성 사전 시작)
        if (resignDate == null) {
            resignDate = resignRepository
                    .findActiveOrConfirmedByEmpId(companyId, empId)
                    .map(Resign::getResignDate)
                    .orElseThrow(() -> new CustomException(ErrorCode.RESIGN_DATE_NOT_SET));
        }

//        근속일수 / 근속연수
        long serviceDays = ChronoUnit.DAYS.between(hireDate, resignDate);
        if (serviceDays < 365){     //근속 1년미만
            throw new CustomException(ErrorCode.SERVICE_PERIOD_TOO_SHORT);
        }
        BigDecimal serviceYears = BigDecimal.valueOf(serviceDays)
                .divide(BigDecimal.valueOf(365), 2, RoundingMode.HALF_UP);

//        직전 3개월 기간
        YearMonth resignYm = YearMonth.from(resignDate);
        List<String> last3Months = buildMonthRange(
                resignYm.minusMonths(3),    //퇴사월에서 3개월전
                resignYm.minusMonths(1));   //퇴사월에서 1개월전

//        직전 3개월 총 일수
        int last3MonthDays = calcTotalDays(resignDate.minusMonths(3), resignDate);

//        급여 데이터 조회(QueryDSL) — 사전 산정 시 데이터 부재 가능 → null 방지
        Long last3MonthPay = severancePaysRepository.sumLast3MonthPay(empId, companyId, last3Months);
        if (last3MonthPay == null || last3MonthPay == 0L) {
            throw new CustomException(ErrorCode.SEVERANCE_NO_PAYROLL_DATA);
        }
//        직전 1년 상여금
        List<String> last12Months = buildMonthRange(
                resignYm.minusMonths(12),
                resignYm.minusMonths(1));
        Long lastYearBonus = severancePaysRepository.sumLastYearBonus(empId, companyId, last12Months);
        if (lastYearBonus == null) lastYearBonus = 0L;     // 상여금은 실제로 0 가능 → fallback OK

//        상여금 가산액 = 직전 1년 상여금 * (3/12)
         BigDecimal bonusAdded = BigDecimal.valueOf(lastYearBonus)
                 .multiply(BigDecimal.valueOf(3))
                 .divide(BigDecimal.valueOf(12),0,RoundingMode.FLOOR);

//         연차수당 (직전연도 기준)
/// 대법원의 판례에 따라 =>
//        평균임금 반영분 : 전년도 미사용연차수당은 퇴직금 평균임금에 포함하고(3/12),
//        별도지급분 : 해당연도 미연차수당은 퇴직정산으로 별도 지급
        AnnualLeaveSeveranceResult leaveResult = calcAnnualLeaveAllowanceForSeverance(empId, companyId, resignDate);
        Long annualLeaveForAvgWage = leaveResult.forAvgWage();
        Long annualLeaveOnRetirement = leaveResult.onRetirement();

//        연차수당 가산액 = 직전 1년 소멸 연차수당 * (3/12) - 상여금과 동일
        BigDecimal annualLeaveAdded = BigDecimal.valueOf(annualLeaveForAvgWage)
                .multiply(BigDecimal.valueOf(3))
                .divide(BigDecimal.valueOf(12),0,RoundingMode.FLOOR);

//        1일 평균임금 = (3개월 임금 + 상여 가산액 + 연차수당 가산액) / 3개월 일수(last3MonthDays)
        BigDecimal totalWageBase = BigDecimal.valueOf(last3MonthPay)
                .add(bonusAdded)
                .add(annualLeaveAdded);

        BigDecimal avgDailyWage = last3MonthDays > 0
                ? totalWageBase.divide(BigDecimal.valueOf(last3MonthDays), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

//        통상임금 비교 (근로기준법 제2조)
//        평균임금이 통상임금보다 낮으면 통상임금을 적용
//        통상임금 = 고정/일률적 지급항목(SALARY+ALLOWANCE, isFixed=true) . 209 * 8
        BigDecimal ordinaryDailyWage = calcOrdinaryDailyWage(empId, companyId);
        if (avgDailyWage.compareTo(ordinaryDailyWage) < 0){
            avgDailyWage = ordinaryDailyWage;
            log.info("[SeveranceService] 평균임금 < 통상임금 -> 통상임금 적용 - emp={}, ordinary={}", empId, ordinaryDailyWage);
        }

//        퇴직금 = 1일 평균임금 * 30 * (근속일수 /365)
        Long severanceAmount = avgDailyWage
                .multiply(BigDecimal.valueOf(30))
                .multiply(BigDecimal.valueOf(serviceDays))
                .divide(BigDecimal.valueOf(365), 0, RoundingMode.FLOOR)
                .longValue();

//        퇴직제도 유형 결정 (1순위: 사원개인 설정, 2순위: 회사 퇴직제도 설정)
        RetirementType retirementType = resolveRetirementType(emp, companyId);

//        DC형: 기적립금 차감
        Long dcDepositedTotal = 0L; //DC형 기적립금 합계
        Long dcDiffAmount = 0L;     //DC형 차액(퇴직금-기적립금 = 실제 추가 지급해야할 금액)

        if (retirementType == RetirementType.DC){
            dcDepositedTotal = severancePaysRepository.sumDcDepositedTotal(empId, companyId);
            dcDiffAmount = Math.max(0, severanceAmount - dcDepositedTotal);
        }

        // 직전 3개월 비과세 누계 → 퇴직소득세 계산에 반영
        Long nonTaxableSum = severanceRepositoryImpl
                .sumNonTaxableLast3Months(empId, companyId, last3Months);
        if (nonTaxableSum == null) nonTaxableSum = 0L;


//        퇴직소득세/지방소득세 자동산출
//        퇴직소득세는 분류과세라서 인적공제(부양가족공제) 없음
//        IRP 이전시 과세이연 -> Calculator가 세액 0원 반환
        boolean irpTransfer = emp.getRetirementType() == RetirementType.DB || emp.getRetirementType() == RetirementType.DC;
        int taxYear = resignDate.getYear();

        RetirementIncomeTaxCalculator.TaxResult taxResult = retirementIncomeTaxCalculator.calculate(severanceAmount,nonTaxableSum, serviceYears, taxYear, irpTransfer);
        Long retirementIncomeTax = taxResult.retirementIncomeTax();
        Long localIncomeTax =taxResult.localIncomeTax();

//        실지급액 = (퇴직금 - 퇴직소득세 - 지방소득세) + 퇴직정산 연차수당 별도지급
        Long netAmount = severanceAmount - retirementIncomeTax - localIncomeTax + annualLeaveOnRetirement;

//        스냅샷 데이터
        String empName = emp.getEmpName();
        String deptName = emp.getDept() != null ? emp.getDept().getDeptName() : null;
        String gradeName = emp.getGrade() != null ? emp.getGrade().getGradeName() : null;
        String workGroupName = emp.getWorkGroup() != null ? emp.getWorkGroup().getGroupName() : null;

//        저장 또는 재산정
        SeverancePays sev;

        if (existing.isPresent()){
            sev = existing.get();
            sev.recalculate(last3MonthPay, lastYearBonus, annualLeaveForAvgWage, annualLeaveOnRetirement, last3MonthDays, avgDailyWage, severanceAmount, dcDepositedTotal, dcDiffAmount);
//            재산정시 세금도 최신 데이터로 재적용
            sev.applyTax(retirementIncomeTax, localIncomeTax, taxYear, irpTransfer);
        } else {
            sev = SeverancePays.builder()
                    .employee(emp)
                    .hireDate(hireDate)
                    .resignDate(resignDate)
                    .retirementType(retirementType)
                    .serviceYears(serviceYears)
                    .serviceDays(serviceDays)
                    .last3MonthDays(last3MonthDays)
                    .lastYearBonus(lastYearBonus)
                    .annualLeaveForAvgWage(annualLeaveForAvgWage)
                    .annualLeaveOnRetirement(annualLeaveOnRetirement)
                    .avgDailyWage(avgDailyWage)
                    .severanceAmount(severanceAmount)
                    .last3MonthPay(last3MonthPay)
                    .taxAmount(retirementIncomeTax)
                    .localIncomeTax(localIncomeTax)
                    .taxYear(taxYear)
                    .irpTransfer(irpTransfer)
                    //실지급액 = (퇴직금 - 세금 - 지방세) + 퇴직정산 연차수당 별도지급
                    .netAmount(netAmount)
                    .dcDepositedTotal(dcDepositedTotal)
                    .dcDiffAmount(dcDiffAmount)
                    .company(company)
                    .empName(empName)
                    .deptName(deptName)
                    .gradeName(gradeName)
                    .workGroupName(workGroupName)
                    .build();

        severancePaysRepository.save(sev);
        }

        log.info("[SeveranceService 퇴직금 산정 완료 - empId:{}, severance={}, nonTax={}, type:{}", empId, severanceAmount, nonTaxableSum, retirementType);

        return SeveranceDetailResDto.fromEntity(sev);
    }


    private PayrollTransferDto buildTransferDto(SeverancePays sev, UUID companyId){
        EmpRetirementAccount account = empRetirementAccountRepository
                .findByEmployee_EmpIdAndCompany_CompanyId(
                        sev.getEmployee().getEmpId(), companyId)
                .orElseThrow(()-> new CustomException(ErrorCode.RETIREMENT_ACCOUNT_NOT_FOUND));

        Long transferAmount = sev.getRetirementType() == RetirementType.DC ? sev.getDcDiffAmount() : sev.getNetAmount();

        return PayrollTransferDto.builder()
                .empName(sev.getEmpName())
                .bankCode(null)     //퇴직연금 계좌는 bankCode 별도관리 필요시 추가
                .accountNumber(account.getAccountNumber().replace("-",""))
                .netPay(transferAmount)
                .memo("퇴직금 "+ sev.getResignDate())
                .build();
    }



//    퇴직제도 유형 설정
//        1. 사원 개인 설정
//        2. 회사 퇴직제도 설정
//        -> 회사가 DB_DC 둘다 운영하는데 개인설정이 없으면 판단 불가 -> 사원설정 필수 -> 예외
//           회사가 단일제도면 그걸 적용
    RetirementType resolveRetirementType(Employee emp, UUID companyId){

        if(emp.getRetirementType() != null){
            return emp.getRetirementType();
        }
        RetirementSettings settings = retirementSettingsRepository.findByCompany_CompanyId(companyId).orElseThrow(()->new CustomException(ErrorCode.RETIREMENT_SETTINGS_NOT_FOUND));

//    DB_DC 병행인 경우 PensionType.toRetirementType() 에서 예외 발생
        return settings.getPensionType().toRetirementType();
    }

    private SeverancePays findSeverance(UUID companyId, Long sevId){
        return severancePaysRepository.findBySevIdAndCompany_CompanyId(sevId, companyId).orElseThrow(()-> new CustomException(ErrorCode.SEVERANCE_NOT_FOUND));
    }

//  연월 문자열 리스트(시작월~종료월) ex) ["2026-01", "2026-02", "2026-03"]
    private List<String> buildMonthRange(YearMonth from, YearMonth to){
        List<String> months = new ArrayList<>();
        YearMonth current = from;
        while (!current.isAfter(to)){
            months.add(current.toString());
            current =current.plusMonths(1);
        }
        return months;
    }

    private int calcTotalDays(LocalDate from, LocalDate to){
        return (int) ChronoUnit.DAYS.between(from, to);
    }


//    퇴직금 산정용 연차수당 합산
    /*연차정책에 따라 소멸일 계산 방식을 다르게
    * FISCAL : 회계년도 종료일에 소멸
    * HIRE : 입사 기념일 전날에 소멸
    * 퇴직일 직전 1년 범위 내에 소멸된 수당만 평균임금 산정 기초에 포함
    * 판정 로직은 LeaveAllowance 엔티티의 isInSeverancePeriod() */
    private AnnualLeaveSeveranceResult calcAnnualLeaveAllowanceForSeverance(Long empId, UUID companyId, LocalDate resignDate){

        VacationPolicy policy = vacationPolicyRepository.findByCompanyId(companyId).orElseThrow(()-> new CustomException(ErrorCode.VACATION_POLICY_NOT_FOUND));

        String fiscalStart = policy.getPolicyFiscalYearStart(); // HIRE이면 null

        List<LeaveAllowance> candidates = leaveAllowanceRepository.findForSeverance(companyId,empId,List.of(AllowanceStatus.CALCULATED, AllowanceStatus.APPLIED));

//       평균임금 반영분 - Resigned 제외, 직전1년 내 소멸분 합산
        long forAvgWage = candidates.stream()
                .filter(la-> la.getAllowanceType() != AllowanceType.RESIGNED)
                .filter(la-> la.isInSeverancePeriod(resignDate, fiscalStart))
                .mapToLong(la-> la.getAllowanceAmount() != null ? la.getAllowanceAmount() : 0L)
                .sum();

//        퇴직정산 별도지급분 - Resigned 타입만 (평균임금 미포함)
        long onRetirement = candidates.stream()
                .filter(la-> la.getAllowanceType() == AllowanceType.RESIGNED)
                .mapToLong(la-> la.getAllowanceAmount() != null ? la.getAllowanceAmount() : 0L)
                .sum();


        log.info("[SeveranceService] 연차수당 합산 - empId{}, policy={}, forAvgWage={}, onRetirement={}, candidates={}", empId,policy.getPolicyBaseType(), forAvgWage, onRetirement, candidates.size());

        return new AnnualLeaveSeveranceResult(forAvgWage, onRetirement);
    }


//    연차수당 산정 결과 - 평균임금 반영분과 퇴직정산 별도지급분을 분리해 전달
    private record AnnualLeaveSeveranceResult(long forAvgWage, long onRetirement){};




    //    통상임금(1일) 산정
    /* 통상임금 = (기본급 + 고정수당) 월액 / 209 * 8
    *
    *  산정기준 :
    *   PayItems.isFixed = true (고정항목)
    *   PayItemCategory IN (SALARY, ALLOWANCE)
    *   제외 : 상여금, 성과급, 연장/야간/휴일수당 등
    * */
    private BigDecimal calcOrdinaryDailyWage(Long empId, UUID companyId){
//        최근 확정 급여에서 기본급 + 고정수당 월액 조회
        Long monthlyOrdinary = severanceRepositoryImpl.sumOrdinaryMonthlyPay(empId, companyId);
        if (monthlyOrdinary == null || monthlyOrdinary == 0L){
            return BigDecimal.ZERO;
        }
//        월 통상임금 / 209 * 8 = 1일 통상임금
        return BigDecimal.valueOf(monthlyOrdinary)
                .divide(BigDecimal.valueOf(209), 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(8))
                .setScale(2, RoundingMode.HALF_UP);
    }


///   퇴직금 목록 조회
    public SeveranceListResDto getSeveranceList(UUID companyId, SevStatus statusFilter, Pageable pageable){
        Page<SeverancePays> page;
        if(statusFilter != null) {
            page = severancePaysRepository.findByCompany_CompanyIdAndSevStatus(companyId, statusFilter, pageable);
        } else {
            page = severancePaysRepository.findByCompany_CompanyId(companyId,pageable);
        }
        Page<SeveranceResDto> dtoPage = page.map(SeveranceResDto::fromEntity);

//        상태별 건수 집계
        List<Object[]> statusCounts = severancePaysRepository.countBySevStatus(companyId);
//        EnumMap : 빠르고 메모리적게씀, 순서보장, 타입안정성(key로 해당enum값만 가능)
        Map<SevStatus, Long> countMap = new EnumMap<>(SevStatus.class);
        for(Object[] row : statusCounts){
            countMap.put((SevStatus) row[0],(Long) row[1]);
        }

//        합계
        long totalSevAmount = page.getContent().stream()
                .mapToLong(SeverancePays::getSeveranceAmount).sum();
        long totalNet = page.getContent().stream()
                .mapToLong(SeverancePays::getNetAmount).sum();

        return SeveranceListResDto.builder()
                .totalCount(page.getTotalElements())
                .calculatingCount(countMap.getOrDefault(SevStatus.CALCULATING, 0L))
                .confirmedCount(countMap.getOrDefault(SevStatus.CONFIRMED,0L))
                .approvedCount(countMap.getOrDefault(SevStatus.APPROVED, 0L))
                .paidCount(countMap.getOrDefault(SevStatus.PAID, 0L))
                .totalSeveranceAmount(totalSevAmount)
                .totalNetAmount(totalNet)
                .severances(dtoPage)
                .build();
    }


///     상세조회
    public SeveranceDetailResDto getSeveranceDetail(UUID companyId, Long sevId){
        SeverancePays sev = findSeverance(companyId, sevId);
        return SeveranceDetailResDto.fromEntity(sev);
    }

///     확정
    @Transactional
    public void confirmSeverance(UUID companyId, Long sevId, Long confirmedBy){
        SeverancePays sev = findSeverance(companyId, sevId);
        sev.confirm(confirmedBy);
    }

///    전자결재 상신
    @Transactional
    public void submitApproval(UUID companyId, Long sevId, Long  approvalDocId) {
        SeverancePays sev = findSeverance(companyId, sevId);
        sev.submitApproval(approvalDocId);
    }


///    전자결재 결과 처리 (kafka consumer에서 호출)
    @Transactional
    public void applyApprovalResult(SeveranceApprovalResultEvent event){
        SeverancePays sev = severancePaysRepository.findBySevIdAndCompany_CompanyId(event.getSevId(), event.getCompanyId()).orElseThrow(() -> new CustomException(ErrorCode.SEVERANCE_NOT_FOUND));

        if (sev.getApprovalDocId() == null && event.getApprovalDocId() != null) {
            sev.bindApprovalDoc(event.getApprovalDocId());
            sev.submitApproval(event.getApprovalDocId());
        }

        String status = event.getStatus();
        if ("APPROVED".equals(status)) {
            sev.approve();
            log.info("[SeveranceService] 전자결재 승인 처리 완료 - severanceId={}",
                    event.getSevId());
        } else if ("REJECTED".equals(status)) {
            sev.rejectApproval();
            log.info("[SeveranceService] 전자결재 반려 처리 - severanceId={}, reason={}",
                    event.getSevId(), event.getRejectReason());
        } else if ("CANCELED".equals(status)) {
            sev.cancelApproval();
            log.info("[SeveranceService] 전자결재 회수 - severanceId={}", event.getSevId());
        } else {
            log.warn("[SeveranceResult] 알 수 없는 status={} - severanceId={}",
                    status, event.getSevId());
        }
    }

}



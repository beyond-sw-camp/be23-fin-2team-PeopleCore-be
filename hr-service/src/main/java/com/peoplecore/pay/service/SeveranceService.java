package com.peoplecore.pay.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.EmpRetirementAccount;
import com.peoplecore.pay.domain.RetirementSettings;
import com.peoplecore.pay.domain.SeverancePays;
import com.peoplecore.pay.dtos.PayrollTransferDto;
import com.peoplecore.pay.dtos.SeveranceCalcReqDto;
import com.peoplecore.pay.dtos.SeveranceDetailResDto;
import com.peoplecore.pay.enums.PensionType;
import com.peoplecore.pay.enums.RetirementType;
import com.peoplecore.pay.enums.SevStatus;
import com.peoplecore.pay.repository.EmpRetirementAccountRepository;
import com.peoplecore.pay.repository.RetirementSettingsRepository;
import com.peoplecore.pay.repository.SeveranceRepository;
import com.peoplecore.pay.repository.SeveranceRepositoryImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@Slf4j
public class SeveranceService {

    private final SeveranceRepository severanceRepository;
    private final CompanyRepository companyRepository;
    private final EmployeeRepository employeeRepository;
    private final EmpRetirementAccountRepository empRetirementAccountRepository;
    private final RetirementSettingsRepository retirementSettingsRepository;
    private final SeveranceRepositoryImpl severanceRepositoryImpl;

    @Autowired
    public SeveranceService(SeveranceRepository severanceRepository, CompanyRepository companyRepository, EmployeeRepository employeeRepository, EmpRetirementAccountRepository empRetirementAccountRepository, RetirementSettingsRepository retirementSettingsRepository, SeveranceRepositoryImpl severanceRepositoryImpl) {
        this.severanceRepository = severanceRepository;
        this.companyRepository = companyRepository;
        this.employeeRepository = employeeRepository;
        this.empRetirementAccountRepository = empRetirementAccountRepository;
        this.retirementSettingsRepository = retirementSettingsRepository;
        this.severanceRepositoryImpl = severanceRepositoryImpl;
    }


// 퇴직금 산정 (퇴직 확정된 사원 대상)
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
        Company company = companyRepository.findById(companyId).orElseThrow(()-> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        Employee emp = employeeRepository.findByEmpIdAndCompany_CompanyId(empId, companyId).orElseThrow(()-> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

//        이미 산정된 것이 있으면 재산정 (CALCULATING 상태일때만)
        Optional<SeverancePays> existing = severanceRepository
                .findByEmployee_EmpIdAndCompany_CompanyId(empId, companyId)
                .stream()
                .filter(s-> s.getSevStatus() == SevStatus.CALCULATING)
                .findFirst();

        LocalDate hireDate = emp.getEmpHireDate();
        LocalDate resignDate = emp.getEmpResignDate();

        if (resignDate == null){
            throw new CustomException(ErrorCode.RESIGN_DATE_NOT_SET);
        }

//        근속일수 / 근속연수
        long serviceDays = ChronoUnit.DAYS.between(hireDate, resignDate);
        if (serviceDays < 365){
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

//        급여 데이터 조회(QueryDSL)
        Long last3MonthPay = severanceRepository.sumLast3MonthPay(empId,companyId,last3Months);

//        직전 1년 상여금
        List<String> last12Months = buildMonthRange(
                resignYm.minusMonths(12),
                resignYm.minusMonths(1));
        Long lastYearBonus = severanceRepository.sumLastYearBonus(empId,companyId, last12Months);

//        상여금 가산액 = 직전 1년 상여금 * (3/12)
         BigDecimal bonusAdded = BigDecimal.valueOf(lastYearBonus)
                 .multiply(BigDecimal.valueOf(3))
                 .divide(BigDecimal.valueOf(12),0,RoundingMode.FLOOR);

//         연차수당 (직전연도 기준)
//         -> 전년도 미사용연차수당은 퇴직금 평균임금에 포함하고, 해당연도 미연차수당은 퇴직정산으로 별도 지급
        Long annualLeaveAllowance = severanceRepository.getAnnualLeaveAllowance(empId, companyId,resignDate.getYear() -1);

//        1일 평균임금
        BigDecimal totalWageBase = BigDecimal.valueOf(last3MonthDays)
                .add(bonusAdded)
                .add(BigDecimal.valueOf(annualLeaveAllowance));

        BigDecimal avgDailyWage = last3MonthDays > 0
                ? totalWageBase.divide(BigDecimal.valueOf(last3MonthDays), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

//        통상임금 비교 (근로기준법 제2조)
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

//        퇴직제도 유형 결정
        RetirementType retirementType = resolveRetirementType(emp, companyId);

//        DC형: 기적립금 차감
        Long dcDepositedTotal = 0L; //DC형 기적립금 합계
        Long dvDiffAmount = 0L;     //DC형 차액(퇴직금-기적립금 = 실제 추가 지급해야할 금액)

        if (retirementType == RetirementType.DC){
            dcDepositedTotal = severanceRepository.sumDcDepositedTotal(empId, companyId);
            dvDiffAmount = Math.max(0, severanceAmount - dcDepositedTotal);
        }

//        스냅샷 데이터
        String empName = emp.getEmpName();
        String deptName = emp.getDept() != null ? emp.getDept().getDeptName() : null;
        String gradeName = emp.getGrade() != null ? emp.getGrade().getGradeName() : null;
        String workGroupName = emp.getWorkGroup() != null ? emp.getWorkGroup().getGroupName() : null;

//        저장 또는 재산정
        SeverancePays sev;

        if (existing.isPresent()){
            sev = existing.get();
            sev.recalculate(last3MonthPay, lastYearBonus, annualLeaveAllowance, last3MonthDays, avgDailyWage, severanceAmount, dcDepositedTotal, dcDepositedTotal);
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
                    .avgDailyWage(avgDailyWage)
                    .severanceAmount(severanceAmount)
                    .last3MonthDays(last3MonthDays)
                    .avgDailyWage(avgDailyWage)
                    .severanceAmount(severanceAmount)
                    .netAmount(severanceAmount)  // 세금 적용 전
                    .dcDepositedTotal(dcDepositedTotal)
                    .dcDiffAmount(dvDiffAmount)
                    .company(company)
                    .empName(empName)
                    .deptName(deptName)
                    .gradeName(gradeName)
                    .workGroupName(workGroupName)
                    .build();

        severanceRepository.save(sev);
        }

        log.info("[SeveranceService 퇴직금 산정 완료 - empId:{}, amount={}, type:{}", empId, dvDiffAmount, retirementType);

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
//        2. 회사 퇴직제도 설정에서 가져오기
//        -> 회사가 DB_DC 둘다 운영하는데 개인설정이 없으면 판단 불가
//           회사가 단일제도면 그걸 적용
    private RetirementType resolveRetirementType(Employee emp, UUID companyId){

        if(emp.getRetirementType() != null){
            return emp.getRetirementType();
        }
        RetirementSettings settings = retirementSettingsRepository.findByCompany_CompanyId(companyId).orElseThrow(()->new CustomException(ErrorCode.RETIREMENT_SETTINGS_NOT_FOUND));

//    DB_DC 병행인 경우 PensionType.toRetirementType() 에서 예외 발생
        return settings.getPensionType().toRetirementType();
    }

    private SeverancePays findSeverance(UUID companyId, Long sevId){
        return severanceRepository.findBySevIdAndCompany_CompanyId(sevId, companyId).orElseThrow(()-> new CustomException(ErrorCode.SEVERANCE_NOT_FOUND));
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
}



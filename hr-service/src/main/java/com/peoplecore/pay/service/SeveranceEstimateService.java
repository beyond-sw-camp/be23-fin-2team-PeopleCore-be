package com.peoplecore.pay.service;

import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.pay.dtos.SeveranceEstimateRowDto;
import com.peoplecore.pay.dtos.SeveranceEstimateSummaryResDto;
import com.peoplecore.pay.enums.RetirementType;
import com.peoplecore.pay.repository.RetirementPensionDepositsRepository;
import com.peoplecore.pay.repository.SeverancePaysRepository;
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
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@Transactional(readOnly = true)
public class SeveranceEstimateService {

    private final EmployeeRepository employeeRepository;
    private final SeverancePaysRepository severancePaysRepository;
    private final SeveranceService severanceService;  // 공통 helper 재사용 (resolveRetirementType 등)
    private final RetirementPensionDepositsRepository retirementPensionDepositsRepository;

    @Autowired
    public SeveranceEstimateService(EmployeeRepository employeeRepository, SeverancePaysRepository severancePaysRepository, SeveranceService severanceService, RetirementPensionDepositsRepository retirementPensionDepositsRepository) {
        this.employeeRepository = employeeRepository;
        this.severancePaysRepository = severancePaysRepository;
        this.severanceService = severanceService;
        this.retirementPensionDepositsRepository = retirementPensionDepositsRepository;
    }

///    근속 1년이상 사원 전체 퇴직금 추계액 계산
//    typeFilter : null 이면 전체, 아니면 해당유형만
    public SeveranceEstimateSummaryResDto getEstimateSummary(UUID companyId, LocalDate baseDate, String typeFilter){
        long startTs = System.currentTimeMillis();    //계산 시작시간

//        1) 재직자 중 근속 1년이상 조회
        List<Employee> targets = employeeRepository.findAllActiveOverOneYear(companyId, List.of(EmpStatus.ACTIVE, EmpStatus.ON_LEAVE), baseDate.minusYears(1));

        log.info("[SeveranceEstimate] 추계액 산정 시작 - companyId={}, baseDate={}, target={}명", companyId, baseDate, targets.size());

//        일괄 조회
        List<Long> empIds = targets.stream().map(Employee::getEmpId).toList();
        YearMonth baseYm = YearMonth.from(baseDate);
        List<String> last3Months = buildMonthRange(baseYm.minusMonths(3), baseYm.minusMonths(1), new ArrayList<>());
        List<String> last12Months = buildMonthRange(baseYm.minusMonths(12), baseYm.minusMonths(1), new ArrayList<>());

        Map<Long, Long> payMap = severancePaysRepository.sumLast3MonthPayByEmpIds(companyId, empIds, last3Months);
        Map<Long, Long> bonusMap = severancePaysRepository.sumLastYearBonusByEmpIds(companyId, empIds, last12Months);
        Map<Long, Long> dcMap = severancePaysRepository.sumDcDepositedTotalByEmpIds(companyId, empIds);


        List<SeveranceEstimateRowDto> rows = new ArrayList<>();

        for (Employee emp : targets){
            try {
                RetirementType rt = severanceService.resolveRetirementType(emp, companyId);

//                typeFilter 적용
                if (typeFilter != null && !typeFilter.isBlank() && !rt.name().equalsIgnoreCase(typeFilter)) continue;

                SeveranceEstimateRowDto rowDto = calculateOneRow(emp, baseDate, rt, payMap.getOrDefault(emp.getEmpId(),0L), bonusMap.getOrDefault(emp.getEmpId(), 0L), dcMap.getOrDefault(emp.getEmpId(), 0L));
                rows.add(rowDto);
            } catch (Exception e) {
                log.warn("[SeveranceEstimate] 사원 산정 실패 - empId={}, reason={}", emp.getEmpId(), e.getMessage());
            }
        }

//        2) 유형별 집계
//        퇴직금인원수, DB형인원수, DB형인원수
        int sevCount = 0, dbCount = 0, dcCount = 0;
//        예상퇴직금총금액, DB형예상총금액, DC형차액총액, 전체부담추정액
        long sevAmt = 0, dbAmt = 0, dcDiff = 0, totalAmt = 0;
        for (SeveranceEstimateRowDto r : rows) {
            totalAmt += r.getDisplayAmount() != null ? r.getDisplayAmount() : 0L;
            switch (r.getRetirementType()) {
                case "severance" -> { sevCount++; sevAmt += r.getEstimatedSeverance(); }
                case "DB"        -> { dbCount++;  dbAmt  += r.getEstimatedSeverance(); }
                case "DC"        -> { dcCount++;  dcDiff += r.getDcDiffAmount() != null ? r.getDcDiffAmount() : 0L; }
            }
        }

        long elapsed = System.currentTimeMillis() - startTs;        //끝났을때 경과시간 - 시작시간
        log.info("[SeveranceEstimate] 추계액 산정 완료 - totalAmount={}, 소요시간={}ms", totalAmt, elapsed);

        return SeveranceEstimateSummaryResDto.builder()
                .baseDate(baseDate)
                .totalEmployees(rows.size())
                .totalEstimateAmount(totalAmt)
                .severanceCount(sevCount).severanceAmount(sevAmt)
                .dbCount(dbCount).dbAmount(dbAmt)
                .dcCount(dcCount).dcDiffAmount(dcDiff)
                .employees(rows)
                .build();
    }


//    사원1명에 대한 추계액 계산
    SeveranceEstimateRowDto calculateOneRow(Employee emp, LocalDate baseDate, RetirementType rt, Long last3MonthPay, Long lastYearBonus, Long dcDepositedTotal){
        LocalDate hireDate = emp.getEmpHireDate();
        long serviceDays = ChronoUnit.DAYS.between(hireDate, baseDate);
        BigDecimal serviceYears = BigDecimal.valueOf(serviceDays)
                .divide(BigDecimal.valueOf(365), 2, RoundingMode.HALF_UP);

        int last3MonthDays = (int) ChronoUnit.DAYS.between(baseDate.minusMonths(3), baseDate);
        Long annualLeaveAllowance = 0L;     //TODO: 연차수당 모듈 연동시 변동

        long bonusAdded = lastYearBonus != null ? (lastYearBonus * 3/12) : 0L;
        long totalWageBase = (last3MonthPay != null ? last3MonthPay : 0L) + bonusAdded + annualLeaveAllowance;

        BigDecimal avgDailyWage = last3MonthDays > 0
                ? BigDecimal.valueOf(totalWageBase).divide(BigDecimal.valueOf(last3MonthDays), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

//        퇴직금 = 평균일급 * 30 * (근속일수 / 365)
        long estimatedSeverance = avgDailyWage
                .multiply(BigDecimal.valueOf(30))
                .multiply(BigDecimal.valueOf(serviceDays))
                .divide(BigDecimal.valueOf(365), 0, RoundingMode.FLOOR)
                .longValue();

//        DC형 처리
        Long dcDiffAmount = null;
        long displayAmount = estimatedSeverance;

        if (rt == RetirementType.DC){
            dcDiffAmount = Math.max(0, estimatedSeverance - (dcDepositedTotal != null ? dcDepositedTotal : 0L));
            displayAmount = dcDiffAmount;
        } else {
            dcDepositedTotal = null;    //DB/퇴직금이면 null유지
        }

        return SeveranceEstimateRowDto.builder()
                .empId(emp.getEmpId())
                .empNum(emp.getEmpNum())
                .empName(emp.getEmpName())
                .deptName(emp.getDept() != null ? emp.getDept().getDeptName() : null)
                .gradeName(emp.getGrade() != null ? emp.getGrade().getGradeName() : null)
                .hireDate(hireDate)
                .serviceYears(serviceYears)
                .retirementType(rt.name())
                .avgDailyWage(avgDailyWage)
                .estimatedSeverance(estimatedSeverance)
                .dcDepositedTotal(dcDepositedTotal)
                .dcDiffAmount(dcDiffAmount)
                .displayAmount(displayAmount)
                .build();
    }


//    특정 구간의 월 목록을 "YYYY-MM" 문자열 배열로 만듦
    private List<String> buildMonthRange(YearMonth from, YearMonth to, List<String> acc){
        if (from.isAfter(to)) return acc;
        acc.add(from.toString());
        return buildMonthRange(from.plusMonths(1), to, acc);
    }
}

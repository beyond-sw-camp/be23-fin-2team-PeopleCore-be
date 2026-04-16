package com.peoplecore.pay.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.*;
import com.peoplecore.pay.dtos.*;
import com.peoplecore.pay.enums.PayItemType;
import com.peoplecore.pay.enums.PayrollStatus;
import com.peoplecore.pay.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class InsuranceSettlementService {

//    4대보험 공제항목 표준명(PayItems.payItemName 매칭)
    private static final String ITEM_PENSION = "국민연금";
    private static final String ITEM_HEALTH = "건강보험";
    private static final String ITEM_LTC = "장기요양보험";
    private static final String ITEM_EMPLOYMENT = "고용보험";
    private static final List<String> INSURANCE_ITEM_NAMES = List.of(ITEM_PENSION, ITEM_HEALTH, ITEM_LTC, ITEM_EMPLOYMENT);

    //    정산보험료 정산전용 PayItems 항목명 (6종)
    private static final String SETTLE_HEALTH_CHARGE     = "건강보험 정산분";
    private static final String SETTLE_LTC_CHARGE        = "장기요양 정산분";
    private static final String SETTLE_EMPLOYMENT_CHARGE = "고용보험 정산분";
    private static final String SETTLE_HEALTH_REFUND     = "건강보험 환급분";
    private static final String SETTLE_LTC_REFUND        = "장기요양 환급분";
    private static final String SETTLE_EMPLOYMENT_REFUND = "고용보험 환급분";

    private static final List<String> SETTLEMENT_ITEM_NAMES = List.of(
            SETTLE_HEALTH_CHARGE, SETTLE_LTC_CHARGE, SETTLE_EMPLOYMENT_CHARGE,
            SETTLE_HEALTH_REFUND, SETTLE_LTC_REFUND, SETTLE_EMPLOYMENT_REFUND
    );


    private final InsuranceSettlementRepository insuranceSettlementRepository;
    private final PayrollRunsRepository payrollRunsRepository;
    private final InsuranceRatesRepository insuranceRatesRepository;
    private final PayrollDetailsRepository payrollDetailsRepository;
    private final PayItemsRepository payItemsRepository;

    @Autowired
    public InsuranceSettlementService(InsuranceSettlementRepository insuranceSettlementRepository, PayrollRunsRepository payrollRunsRepository, InsuranceRatesRepository insuranceRatesRepository, PayrollDetailsRepository payrollDetailsRepository, PayItemsRepository payItemsRepository) {
        this.insuranceSettlementRepository = insuranceSettlementRepository;
        this.payrollRunsRepository = payrollRunsRepository;
        this.insuranceRatesRepository = insuranceRatesRepository;
        this.payrollDetailsRepository = payrollDetailsRepository;
        this.payItemsRepository = payItemsRepository;
    }


    //    1. 정산보험료 목록조회
    public InsuranceSettlementSummaryResDto getSettlementList(UUID companyId, String fromYearMonth, String toYearMonth, Pageable pageable){

//        합계용 전체조회(페이징X)
        List<InsuranceSettlement> settlements = insuranceSettlementRepository.findAllByPeriod(companyId, fromYearMonth, toYearMonth);

        if (settlements.isEmpty()){
            return InsuranceSettlementSummaryResDto.builder()
                    .settlementFromMonth(fromYearMonth)
                    .settlementToMonth(toYearMonth)
                    .totalEmployees(0)
                    .appliedCount(0).totalChargeAmount(0L).totalRefundAmount(0L)
                    .totalBaseSalary(0L)
                    .totalPensionEmployee(0L).totalPensionEmployer(0L)
                    .totalHealthEmployee(0L).totalHealthEmployer(0L)
                    .totalLtcEmployee(0L).totalLtcEmployer(0L)
                    .totalEmploymentEmployee(0L).totalEmploymentEmployer(0L)
                    .totalIndustrialEmployer(0L)
                    .grandTotalEmployee(0L).grandTotalEmployer(0L)
                    .grandTotalDeducted(0L).grandTotalDiff(0L)
                    .settlements(List.of())
                    .build();
        }

//        합계계산
        long sumBase = 0, sumPenEmp = 0, sumPenEmpr = 0;
        long sumHlthEmp = 0, sumHlthEmpr = 0;
        long sumLtcEmp = 0, sumLtcEmpr = 0;
        long sumEmpInsEmp = 0, sumEmpInsEmpr = 0;
        long sumIndEmpr = 0;
        long sumTotalEmp = 0, sumTotalEmpr = 0;
        long sumTotalDeducted = 0, sumTotalDiff = 0;
        int appliedCount = 0;
        long totalCharge = 0, totalRefund = 0;

        for(InsuranceSettlement s : settlements){
            sumBase += s.getBaseSalary();
            sumPenEmp += s.getPensionEmployee();
            sumPenEmpr += s.getPensionEmployer();
            sumHlthEmp += s.getHealthEmployee();
            sumHlthEmpr += s.getHealthEmployer();
            sumLtcEmp += s.getLtcEmployee();
            sumLtcEmpr += s.getLtcEmployer();
            sumEmpInsEmp += s.getEmploymentEmployee();
            sumEmpInsEmpr += s.getEmploymentEmployer();
            sumIndEmpr += s.getIndustrialEmployer();
            sumTotalEmp += s.getTotalEmployee();
            sumTotalEmpr += s.getTotalEmployer();
            sumTotalDeducted += s.getTotalDeducted();
            sumTotalDiff += s.getTotalDiff();

            if (Boolean.TRUE.equals(s.getIsApplied())) appliedCount++;
            if (s.getTotalDiff() > 0) totalCharge += s.getTotalDiff();
            if (s.getTotalDiff() < 0) totalRefund += Math.abs(s.getTotalDiff());
        }

        // 테이블용 페이징 조회
        Page<InsuranceSettlement> pageResult = insuranceSettlementRepository
                .findPageByPeriod(companyId, fromYearMonth, toYearMonth,pageable);

        List<InsuranceSettlementResDto> dtos = pageResult.getContent().stream()
                .map(InsuranceSettlementResDto::fromEntity)
                .collect(Collectors.toList());

        return InsuranceSettlementSummaryResDto.builder()
                .settlementFromMonth(fromYearMonth)
                .settlementToMonth(toYearMonth)
                .totalEmployees(settlements.size())
                .appliedCount(appliedCount)
                .totalChargeAmount(totalCharge)
                .totalRefundAmount(totalRefund)
                .totalBaseSalary(sumBase)
                .totalPensionEmployee(sumPenEmp)
                .totalPensionEmployer(sumPenEmpr)
                .totalHealthEmployee(sumHlthEmp)
                .totalHealthEmployer(sumHlthEmpr)
                .totalLtcEmployee(sumLtcEmp)
                .totalLtcEmployer(sumLtcEmpr)
                .totalEmploymentEmployee(sumEmpInsEmp)
                .totalEmploymentEmployer(sumEmpInsEmpr)
                .totalIndustrialEmployer(sumIndEmpr)
                .grandTotalEmployee(sumTotalEmp)
                .grandTotalEmployer(sumTotalEmpr)
                .grandTotalDeducted(sumTotalDeducted)
                .grandTotalDiff(sumTotalDiff)
                .settlements(dtos)
                .build();
    }


//    보험료 산정 (정산기간 기반)
    @Transactional
    public InsuranceSettlementSummaryResDto calculateSettlement(UUID companyId, InsuranceSettlementCalcReqDto reqDto, Pageable pageable) {

        String fromMonth = reqDto.getFromYearMonth();
        String toMonth = reqDto.getToYearMonth();

//        1. 정산기간내 지급된(PAID) 급여대장 조회
        List<PayrollRuns> paidRuns = payrollRunsRepository.findByCompany_CompanyIdAndPayrollStatusAndPayYearMonthBetween(companyId, PayrollStatus.PAID, fromMonth, toMonth);

        if (paidRuns.isEmpty()) {
            throw new CustomException(ErrorCode.PAYROLL_NOT_FOUND);
        }

//        2. 보험요율 조회 (정산시작 연도 기준)
        int year = Integer.parseInt(fromMonth.substring(0, 4));
        InsuranceRates rates = insuranceRatesRepository.findByCompany_CompanyIdAndYear(companyId, year).orElseThrow(() -> new CustomException(ErrorCode.INSURANCE_RATES_NOT_FOUND));

//        3. PAID 급여대장의 사원별 지급항목(PAYMENT) 합산 -> 보수총액
//        empId별 지급항목 합계 집계
        Map<Long, Long> empBaseSalaryMap = new HashMap<>();
        Map<Long, Employee> empMap = new HashMap<>();

        for (PayrollRuns run : paidRuns){
            List<PayrollDetails> details = payrollDetailsRepository.findByPayrollRuns(run);
            for (PayrollDetails d : details){
                Long empId = d.getEmployee().getEmpId();
                empMap.putIfAbsent(empId, d.getEmployee());
                if(d.getPayItemType() == PayItemType.PAYMENT){
                    empBaseSalaryMap.merge(empId, d.getAmount(), Long::sum);
                }
            }
        }

//        4. 정산기간 내 지급완료 급여대장에서 기공제액 조회
        List<InsuranceDeductionSummary> deductionSummaries = payrollDetailsRepository.sumDeductionsByEmpAndItem(
                companyId, PayrollStatus.PAID,
                fromMonth, toMonth,
                PayItemType.DEDUCTION, INSURANCE_ITEM_NAMES);


        Map<Long, Map<String, Long>> empDeductedMap = new HashMap<>();
        for (InsuranceDeductionSummary ds : deductionSummaries) {
            empDeductedMap.computeIfAbsent(ds.getEmpId(), k -> new HashMap<>())
                    .put(ds.getPayItemName(), ds.getTotalAmount());
        }

//        5. 기존 정산데이터가 있으면 삭제 후 재생성
        if (insuranceSettlementRepository.existsByCompany_CompanyIdAndSettlementFromMonthAndSettlementToMonth(companyId, fromMonth, toMonth)) {
            insuranceSettlementRepository.deleteByCompany_CompanyIdAndSettlementFromMonthAndSettlementToMonth(companyId, fromMonth, toMonth);
        }

//        6. 사원별 정산 계산
        List<InsuranceSettlement> newSettlements = new ArrayList<>();
        Company company = paidRuns.get(0).getCompany();

        for (Map.Entry<Long, Long> entry : empBaseSalaryMap.entrySet()) {
            Long empId = entry.getKey();
            Long baseSalary = entry.getValue();
            Employee emp = empMap.get(empId);

//            산재보험요율 : 사원의 업종별 요율, 없으면 기본요율
            BigDecimal industrialRate = rates.getIndustrialAccident();
            if (emp.getJobTypes() != null && emp.getJobTypes().getIndustrialAccidentRate() != null) {
                industrialRate = emp.getJobTypes().getIndustrialAccidentRate();
            }

            long pensionBase = baseSalary;
//            국민연금 : 보수월액에 상/하한 적용
            long monthCount = countMonths(fromMonth, toMonth);
            long upperTotal = rates.getPensionUpperLimit() * monthCount;
            long lowerTotal = rates.getPensionLowerLimit() * monthCount;
            if (pensionBase > upperTotal) { pensionBase = upperTotal;
            } else if (pensionBase < lowerTotal) { pensionBase = lowerTotal;
            }

            long pensionEmp = calcHalf(pensionBase, rates.getNationalPension());
            long pensionEmpr = pensionEmp;

//            건강보험
            long healthEmp = calcHalf(baseSalary, rates.getHealthInsurance());
            long healthEmpr = healthEmp;

//            장기요양 : 건강보험료 * 장기요양 요율
            long healthTotal = healthEmp + healthEmpr;
            long ltcTotal = calcAmount(healthTotal, rates.getLongTermCare());
            long ltcEmp = ltcTotal / 2;
            long ltcEmpr = ltcTotal - ltcEmp;   //홀수원 처리

//            고용보험
            long employmentEmp = calcAmount(baseSalary, rates.getEmploymentInsurance());
            long employmentEmpr = calcAmount(baseSalary, rates.getEmploymentInsuranceEmployer());

//            산재보험
            long industrialEmpr = calcAmount(baseSalary, industrialRate);

            // ── 기공제액 ──
            Map<String, Long> deducted = empDeductedMap.getOrDefault(empId, Map.of());
            long dedPension = deducted.getOrDefault(ITEM_PENSION, 0L);
            long dedHealth = deducted.getOrDefault(ITEM_HEALTH, 0L);
            long dedLtc = deducted.getOrDefault(ITEM_LTC, 0L);
            long dedEmployment = deducted.getOrDefault(ITEM_EMPLOYMENT, 0L);

//            합계
            long totalEmp = pensionEmp + healthEmp + ltcEmp + employmentEmp;
            long totalEmpr = pensionEmpr + healthEmpr + ltcEmpr + employmentEmpr + industrialEmpr;
            long totalDeducted = dedPension + dedHealth + dedLtc + dedEmployment;

            InsuranceSettlement settlement = InsuranceSettlement.builder()
                    .payYearMonth(toMonth)  //정산기준월
                    .settlementFromMonth(fromMonth)
                    .settlementToMonth(toMonth)
                    .baseSalary(baseSalary)
//                    정산액
                    .pensionEmployee(pensionEmp)
                    .pensionEmployer(pensionEmpr)
                    .healthEmployee(healthEmp)
                    .healthEmployer(healthEmpr)
                    .ltcEmployee(ltcEmp)
                    .ltcEmployer(ltcEmpr)
                    .employmentEmployee(employmentEmp)
                    .employmentEmployer(employmentEmpr)
                    .industrialEmployer(industrialEmpr)
                    .totalEmployee(totalEmp)
                    .totalEmployer(totalEmpr)
                    .totalAmount(totalEmp + totalEmpr)
//                    기공제액
                    .deductedPension(dedPension)
                    .deductedHealth(dedHealth)
                    .deductedLtc(dedLtc)
                    .deductedEmployment(dedEmployment)
                    .totalDeducted(totalDeducted)
//                    차액
                    .diffPension(pensionEmp - dedPension)
                    .diffHealth(healthEmp - dedHealth)
                    .diffLtc(ltcEmp - dedLtc)
                    .diffEmployment(employmentEmp - dedEmployment)
                    .totalDiff(totalEmp - totalDeducted)

                    .isApplied(false)
                    .company(company)
                    .employee(emp)
                    .payrollRuns(paidRuns.get(paidRuns.size() - 1)) //마지막 급여대장 참조
                    .insuranceRates(rates)
                    .build();

            newSettlements.add(settlement);
        }

        insuranceSettlementRepository.saveAll(newSettlements);
//        조회 후 반환
        return getSettlementList(companyId, fromMonth, toMonth,pageable);

    }


//      사원별 보험료 상세 조회
    public InsuranceSettlementDetailResDto getSettlementDetail(UUID companyId, Long settlementId){
        InsuranceSettlement settlement = insuranceSettlementRepository.findDetailById(settlementId, companyId).orElseThrow(()-> new CustomException(ErrorCode.INSURANCE_SETTLEMENT_NOT_FOUND));

        return InsuranceSettlementDetailResDto.fromEntity(settlement);
    }



//    정산보험료 - 급여대장에 반영 (diff 기반, 국민연금 제외)
    @Transactional
    public void applyToPayroll(UUID companyId, InsuranceSettlementApplyReqDto reqDto){

//        1. 정산기간으로 배치 조회
        List<InsuranceSettlement> settlements = insuranceSettlementRepository
                .findAllByPeriod(companyId, reqDto.getFromYearMonth(), reqDto.getToYearMonth());

        if (settlements.isEmpty()){
            throw new CustomException(ErrorCode.INSURANCE_SETTLEMENT_NOT_FOUND);
        }

//        2. 대상 월 급여대장
        PayrollRuns targetRun = payrollRunsRepository
                .findByCompany_CompanyIdAndPayYearMonth(companyId, reqDto.getTargetPayYearMonth())
                .orElseThrow(() -> new CustomException(ErrorCode.PAYROLL_NOT_FOUND));

        if (targetRun.getPayrollStatus() != PayrollStatus.CALCULATING){
            throw new CustomException(ErrorCode.PAYROLL_STATUS_INVALID);
        }

//        3. 정산전용 PayItems 6종 조회 (isSystem=true)
        Map<String, PayItems> settlementItemMap = loadSettlementPayItems(companyId);

//        4. 사원별 diff 반영 (국민연금 제외)
        for(InsuranceSettlement s : settlements) {
            if (Boolean.TRUE.equals(s.getIsApplied())) continue;

            String period = s.getSettlementFromMonth() + "~" + s.getSettlementToMonth();

//            건강보험 diff: 양수 → 정산분(추가징수/DEDUCTION), 음수 → 환급분(PAYMENT)
            createSettlementDetail(targetRun, s, s.getDiffHealth(),
                    settlementItemMap.get(SETTLE_HEALTH_CHARGE),
                    settlementItemMap.get(SETTLE_HEALTH_REFUND),
                    "건강보험 정산 (" + period + ")");

//            장기요양 diff
            createSettlementDetail(targetRun, s, s.getDiffLtc(),
                    settlementItemMap.get(SETTLE_LTC_CHARGE),
                    settlementItemMap.get(SETTLE_LTC_REFUND),
                    "장기요양 정산 (" + period + ")");

//            고용보험 diff
            createSettlementDetail(targetRun, s, s.getDiffEmployment(),
                    settlementItemMap.get(SETTLE_EMPLOYMENT_CHARGE),
                    settlementItemMap.get(SETTLE_EMPLOYMENT_REFUND),
                    "고용보험 정산 (" + period + ")");

            s.markApplied();
        }

//        5. 급여대장 합계 재계산
        recalculateTotals(targetRun);
    }



//    정산전용 PayItems 6종 로딩(건강,장기요양,고용 - 추가징수/환급
    private Map<String, PayItems> loadSettlementPayItems(UUID companyId) {
        List<PayItems> items = payItemsRepository
                .findByCompany_CompanyIdAndPayItemNameInAndIsSystemTrue(companyId, SETTLEMENT_ITEM_NAMES);

        Map<String, PayItems> map = items.stream()
                .collect(Collectors.toMap(PayItems::getPayItemName, p -> p));

        for (String name : SETTLEMENT_ITEM_NAMES) {
            if (!map.containsKey(name)) {
                throw new CustomException(ErrorCode.INSURANCE_PAY_ITEM_NOT_FOUND);
            }
        }
        return map;
    }

//    diff 기반 정산 상세 1건 생성 (양수 → charge항목/DEDUCTION, 음수 → refund항목/PAYMENT)
    private void createSettlementDetail(PayrollRuns targetRun, InsuranceSettlement settlement,
                                        Long diff, PayItems chargeItem, PayItems refundItem, String memo) {
        if (diff == null || diff == 0L) return;

        PayItems targetItem;
        PayItemType targetType;
        long targetAmount;

        if (diff > 0) {
//            추가징수 → 공제항목
            targetItem = chargeItem;
            targetType = PayItemType.DEDUCTION;
            targetAmount = diff;
        } else {
//            환급 → 지급항목
            targetItem = refundItem;
            targetType = PayItemType.PAYMENT;
            targetAmount = Math.abs(diff);
        }

        PayrollDetails detail = PayrollDetails.builder()
                .payrollRuns(targetRun)
                .employee(settlement.getEmployee())
                .payItems(targetItem)
                .payItemName(targetItem.getPayItemName())
                .payItemType(targetType)
                .amount(targetAmount)
                .memo(memo)
                .company(settlement.getCompany())
                .build();

        payrollDetailsRepository.save(detail);
    }

//    보수월액 * 요율(반올림, 원단위)
        private long calcAmount(long base, BigDecimal rate){
            return BigDecimal.valueOf(base)
                    .multiply(rate)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();
        }

//        보수월액 * 요율 /2 (근로자/사업주 반씩, 반올림)
        private long calcHalf(long base, BigDecimal rate){
        return BigDecimal.valueOf(base)
                .multiply(rate)
                .divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP)
                .longValue();
    }

//    정산기간 개월 수 계산 (예: "2025-04" ~ "2026-03" → 12)
    private long countMonths(String fromYearMonth, String toYearMonth) {
        int fromYear = Integer.parseInt(fromYearMonth.substring(0, 4));
        int fromMonth = Integer.parseInt(fromYearMonth.substring(5, 7));
        int toYear = Integer.parseInt(toYearMonth.substring(0, 4));
        int toMonth = Integer.parseInt(toYearMonth.substring(5, 7));
        return (toYear - fromYear) * 12L + (toMonth - fromMonth) + 1;
    }

//        급여대장 합계 재계산
    private void recalculateTotals(PayrollRuns run){
        List<PayrollDetails> allDetails = payrollDetailsRepository.findByPayrollRuns(run);

        long totalPay = allDetails.stream().filter(d-> d.getPayItemType() == PayItemType.PAYMENT).mapToLong(PayrollDetails::getAmount).sum();
        long totalDeduction = allDetails.stream().filter(d -> d.getPayItemType() == PayItemType.DEDUCTION).mapToLong(PayrollDetails::getAmount).sum();

        int empCount = (int) allDetails.stream().map(d -> d.getEmployee().getEmpId()).distinct().count();

        run.updateTotals(empCount, totalPay, totalDeduction, totalPay - totalDeduction  );
    }

}

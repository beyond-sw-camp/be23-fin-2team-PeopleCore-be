package com.peoplecore.pay.tax;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 퇴직소득세 / 지방소득세 계산기

 * IRP 이전 시에는 과세이연(근퇴법 제17조) → 세액 0원 반환.
 */
@Slf4j
@Component
public class RetirementIncomeTaxCalculator {

     /* 퇴직소득세 + 지방소득세 산출
     *
     * @param severanceAmount    퇴직급여 산정액 (비과세소득 없음 가정)
     * @param nonTaxableAmount   비과세 누계 (직전 3개월 식대/교통비 등 한도 차감 합계). null/음수는 0으로 보정.
     * @param serviceYears       근속연수 (소수점 포함 — 내부에서 1년 미만 절상 처리)
     * @param taxYear            세액 귀속연도 (퇴직일 기준)
     * @param irpTransfer        IRP 이전 여부 — true 시 과세이연으로 세액 0원
     */
    public TaxResult calculate(Long severanceAmount,
                               Long nonTaxableAmount,
                               BigDecimal serviceYears,
                               int taxYear,
                               boolean irpTransfer) {

        // IRP 이전 → 과세이연
        if (irpTransfer) {
            log.info("[TaxCalc] IRP 이전 → 과세이연 — severance={}", severanceAmount);
            return TaxResult.zero();
        }

        if (severanceAmount == null || severanceAmount <= 0L) {
            return TaxResult.zero();
        }

        TaxYearlyConfig.validateSupportedYear(taxYear);

        // 근속연수 1년 미만 절상 (소득세법 시행령 제105조 제3항)
        //   예) 4년 3개월 → 5년 / 10년 11개월 → 11년
        int yearsRoundedUp = serviceYears.setScale(0, RoundingMode.CEILING).intValue();
        if (yearsRoundedUp < 1) yearsRoundedUp = 1;

        // 1. 퇴직소득금액  = 퇴직급여 − 비과세 누계
        long nonTax = (nonTaxableAmount == null) ? 0L : Math.max(0L, nonTaxableAmount);
        long taxableIncome = Math.max(0L, severanceAmount - nonTax);

        if (taxableIncome <= 0L) {
            log.info("[TaxCalc] 비과세 차감 후 과세대상 ≤ 0 → 세액 0원");
            return TaxResult.zero();
        }

        // 2. 근속연수공제
        long serviceYearsDeduction = TaxYearlyConfig.serviceYearsDeduction(taxYear, yearsRoundedUp);

        // 3. 환산급여 = (퇴직소득금액 − 근속연수공제) × 12 / 근속연수
        long afterServiceDeduction = Math.max(0L, taxableIncome - serviceYearsDeduction);
        long annualized = BigDecimal.valueOf(afterServiceDeduction)
                .multiply(BigDecimal.valueOf(12))
                .divide(BigDecimal.valueOf(yearsRoundedUp), 0, RoundingMode.DOWN)
                .longValue();

        if (annualized <= 0L) {
            log.info("[TaxCalc] 환산급여 ≤ 0 → 세액 0원");
            return TaxResult.zero();
        }

        // 4. 환산급여공제
        long annualizedDeduction = TaxYearlyConfig.annualizedDeduction(taxYear, annualized);

        // 5. 과세표준
        long taxBase = Math.max(0L, annualized - annualizedDeduction);

        // 6. 환산산출세액 (기본세율, 누진공제)
        long annualizedTax = TaxYearlyConfig.progressiveTax(taxYear, taxBase);

        // 7. 퇴직소득세 = 환산산출세액 × 근속연수 / 12
        long retirementIncomeTax = BigDecimal.valueOf(annualizedTax)
                .multiply(BigDecimal.valueOf(yearsRoundedUp))
                .divide(BigDecimal.valueOf(12), 0, RoundingMode.DOWN)
                .longValue();
        retirementIncomeTax = Math.max(0L, retirementIncomeTax);

        // 8. 지방소득세 = 퇴직소득세 × 10% (지방세법 제103조의2)
        long localIncomeTax = retirementIncomeTax / 10;

        log.info("[TaxCalc] 퇴직소득세 산출 - severance={}, nonTax={}, years={}, annualized={}, "
                        + "taxBase={}, retTax={}, localTax={}",
                severanceAmount, nonTax, yearsRoundedUp, annualized, taxBase,
                retirementIncomeTax, localIncomeTax);

        return new TaxResult(retirementIncomeTax, localIncomeTax);
    }

    /**
     * 세액 산출 결과
     */
    public record TaxResult(long retirementIncomeTax, long localIncomeTax) {
        public static TaxResult zero() {
            return new TaxResult(0L, 0L);
        }

        public long total() {
            return retirementIncomeTax + localIncomeTax;
        }
    }
}

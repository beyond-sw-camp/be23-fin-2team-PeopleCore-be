package com.peoplecore.pay.tax;

import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;

/**
 * 퇴직소득세 계산에 필요한 연도별 공제구간·세율 상수
 * - 소득세법 제48조 (근속연수공제)
 * - 소득세법 제48조 (환산급여공제)
 * - 소득세법 제55조 (종합소득세 기본세율)

 * 2026년 귀속 기준. 세법 개정 시 연도별 분기 추가.
 */

//근속연도별 공제구간, 세율 관리
public class TaxYearlyConfig {

    private TaxYearlyConfig() {};


     /** 근속연수공제 (소득세법 제48조)
     *   · 5년 이하      : 100만원 × 근속연수
     *   · 6 ~ 10년      : 500만원 + 200만원 × (근속연수 − 5)
     *   · 11 ~ 20년     : 1,500만원 + 250만원 × (근속연수 − 10)
     *   · 20년 초과     : 4,000만원 + 300만원 × (근속연수 − 20)
     */
    public static long serviceYearsDeduction(int taxYear, int years) {
        if (years <= 0) return 0L;
        if (years <= 5) {
            return 1_000_000L * years;
        } else if (years <= 10) {
            return 5_000_000L + 2_000_000L * (years - 5);
        } else if (years <= 20) {
            return 15_000_000L + 2_500_000L * (years - 10);
        } else {
            return 40_000_000L + 3_000_000L * (years - 20);
        }
    }

    /*
     * 환산급여공제 (소득세법 제48조)
     *   · 800만원 이하       : 전액 공제
     *   · 800만 ~ 7,000만   : 800만 + (환산급여 − 800만) × 60%
     *   · 7,000만 ~ 1억     : 4,520만 + (환산급여 − 7,000만) × 55%
     *   · 1억 ~ 3억         : 6,170만 + (환산급여 − 1억) × 45%
     *   · 3억 초과          : 15,170만 + (환산급여 − 3억) × 35%
     */
    public static long annualizedDeduction(int taxYear, long annualized) {
        if (annualized <= 0) return 0L;
        if (annualized <= 8_000_000L) {
            return annualized;
        } else if (annualized <= 70_000_000L) {
            return 8_000_000L + (annualized - 8_000_000L) * 60 / 100;
        } else if (annualized <= 100_000_000L) {
            return 45_200_000L + (annualized - 70_000_000L) * 55 / 100;
        } else if (annualized <= 300_000_000L) {
            return 61_700_000L + (annualized - 100_000_000L) * 45 / 100;
        } else {
            return 151_700_000L + (annualized - 300_000_000L) * 35 / 100;
        }
    }

    /*
     * 기본세율 — 누진공제 방식 (소득세법 제55조, 2026년 귀속)
     *   · 1,400만 이하       :  6%
     *   · 1,400 ~ 5,000만   : 15% (누진공제 126만)
     *   · 5,000 ~ 8,800만   : 24% (누진공제 576만)
     *   · 8,800만 ~ 1.5억   : 35% (누진공제 1,544만)
     *   · 1.5억 ~ 3억        : 38% (누진공제 1,994만)
     *   · 3억 ~ 5억          : 40% (누진공제 2,594만)
     *   · 5억 ~ 10억         : 42% (누진공제 3,594만)
     *   · 10억 초과          : 45% (누진공제 6,594만)
     */
    public static long progressiveTax(int taxYear, long taxBase) {
        if (taxBase <= 0) return 0L;
        if (taxBase <= 14_000_000L) {
            return taxBase * 6 / 100;
        } else if (taxBase <= 50_000_000L) {
            return taxBase * 15 / 100 - 1_260_000L;
        } else if (taxBase <= 88_000_000L) {
            return taxBase * 24 / 100 - 5_760_000L;
        } else if (taxBase <= 150_000_000L) {
            return taxBase * 35 / 100 - 15_440_000L;
        } else if (taxBase <= 300_000_000L) {
            return taxBase * 38 / 100 - 19_940_000L;
        } else if (taxBase <= 500_000_000L) {
            return taxBase * 40 / 100 - 25_940_000L;
        } else if (taxBase <= 1_000_000_000L) {
            return taxBase * 42 / 100 - 35_940_000L;
        } else {
            return taxBase * 45 / 100 - 65_940_000L;
        }
    }

    /**
     * 유효성 검증 — 지원 연도인지 확인
     * (세법 개정 미반영 상태에서의 오계산 방지)
     */
    public static void validateSupportedYear(int taxYear) {
        if (taxYear < 2023 || taxYear > 2026) {
            throw new CustomException(ErrorCode.TAX_YEAR_NOT_SUPPORTED);
        }
    }
}

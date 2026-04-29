package com.peoplecore.pay.support;


import com.peoplecore.pay.domain.PayItems;

/* 항목 금액을 과세 / 비과세로 분리하는 헬퍼
    isTaxable == true  → 전액 과세
    isTaxable == false:
        taxExemptLimit > 0   → 한도까지 비과세, 초과분 과세
        taxExemptLimit == 0  → 전액 비과세 (한도 미설정 = 무제한 비과세)
*/
public final class TaxableCalc {

    private TaxableCalc() {}

    /** 해당 항목 금액 중 과세대상 금액 */
    public static long taxablePart(PayItems item, long amount) {
        long amt = Math.max(0L, amount);
        if (item == null) return amt;
        // isTaxable=true → 전액 과세
        if (!Boolean.FALSE.equals(item.getIsTaxable())) return amt;
        // isTaxable=false → 한도까지 비과세, 초과분 과세 (cap=0 이면 전액 비과세)
        int cap = item.getTaxExemptLimit() == null ? 0 : item.getTaxExemptLimit();
        if (cap <= 0) return 0L;
        return Math.max(0L, amt - cap);
    }

    /** 해당 항목 금액 중 비과세 금액 */
    public static long nonTaxablePart(PayItems item, long amount) {
        long amt = Math.max(0L, amount);
        if (item == null) return 0L;
        // isTaxable=true → 비과세 없음
        if (!Boolean.FALSE.equals(item.getIsTaxable())) return 0L;
        // isTaxable=false
        int cap = item.getTaxExemptLimit() == null ? 0 : item.getTaxExemptLimit();
        if (cap <= 0) return amt;          // cap 미설정 = 전액 비과세
        return Math.min(amt, cap);          // cap 설정 = 한도까지 비과세
    }
}


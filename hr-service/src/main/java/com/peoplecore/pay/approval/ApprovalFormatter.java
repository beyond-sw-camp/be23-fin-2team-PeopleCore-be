package com.peoplecore.pay.approval;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 결의서 dataMap 빌드 시 사용하는 포맷 유틸
 * - 모든 메서드 static (stateless)
 * - 두 Draft Service(Payroll / Severance)에서 공용 사용
 */
public final class ApprovalFormatter {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ISO_LOCAL_DATE;

    private ApprovalFormatter() {
        // 인스턴스화 방지
    }

    /**
     * 천단위 콤마 포맷 — "1,234,567"
     * null/0 안전
     */
    public static String currency(Long n) {
        return n == null ? "0" : String.format("%,d", n);
    }

    /**
     * LocalDate → "yyyy-MM-dd"
     * null 이면 빈 문자열
     */
    public static String date(LocalDate date) {
        return date == null ? "" : date.format(YMD);
    }

    /**
     * 근속일수 → "N년 M개월 D일"
     * (SeveranceApprovalDraftService 에서 주로 사용)
     */
    public static String formatServicePeriod(Long days) {
        if (days == null || days <= 0) return "0일";
        long years = days / 365;
        long remDays = days % 365;
        long months = remDays / 30;
        long onlyDays = remDays % 30;
        return String.format("%d년 %d개월 %d일", years, months, onlyDays);
    }

    /**
     * 평균임금 × 3/12 가산액
     * - 상여금/연차수당 가산액 계산
     * - 근로기준법 시행령 제2조, 대법원 95다2562 판례 공식
     */
    public static Long calc3of12(Long base) {
        if (base == null || base == 0L) return 0L;
        return BigDecimal.valueOf(base)
                .multiply(BigDecimal.valueOf(3))
                .divide(BigDecimal.valueOf(12), 0, RoundingMode.FLOOR)
                .longValue();
    }

    /**
     * 기간 라벨 — "yyyy-MM-dd ~ yyyy-MM-dd"
     */
    public static String period(LocalDate from, LocalDate to) {
        if (from == null || to == null) return "";
        return from.format(YMD) + " ~ " + to.format(YMD);
    }
}
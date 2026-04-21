package com.peoplecore.pay.repository;

import java.util.List;
import java.util.UUID;

public interface SeverancePaysRepositoryCustom {
//    QueryDSL 인터페이스

//    최근 3개월 급여 총액 (지급항목만)
    Long sumLast3MonthPay(Long empId, UUID companyId, List<String> months);

//    직전 1년 상여금 총액
    Long sumLastYearBonus(Long empId, UUID companyId, List<String> months);

//    DB형 기적립금 합계
    Long sumDcDepositedTotal(Long empId, UUID companyId);

//    통상임금(월) 조회 (기본급 + 고정수당)
    Long sumOrdinaryMonthlyPay(Long empId, UUID companyId);

}

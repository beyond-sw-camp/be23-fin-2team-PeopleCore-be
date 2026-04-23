package com.peoplecore.pay.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SeverancePaysRepositoryCustom {
//    QueryDSL 인터페이스

//    최근 3개월 급여 총액 (지급항목만)
    Long sumLast3MonthPay(Long empId, UUID companyId, List<String> months);

//    직전 1년 상여금 총액
    Long sumLastYearBonus(Long empId, UUID companyId, List<String> months);

//    DC형 기적립금 합계
    Long sumDcDepositedTotal(Long empId, UUID companyId);

//    통상임금(월) 조회 (기본급 + 고정수당)
    Long sumOrdinaryMonthlyPay(Long empId, UUID companyId);


//  3개월급여총액, 1년상여금총액, DC형적립급합계
    Map<Long, Long> sumLast3MonthPayByEmpIds(UUID companyId, List<Long> empIds, List<String> months);
    Map<Long, Long> sumLastYearBonusByEmpIds(UUID companyId, List<Long> empIds, List<String> months);
    Map<Long, Long> sumDcDepositedTotalByEmpIds(UUID companyId, List<Long> empIds);
}

package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.*;
import com.peoplecore.pay.dto.PayStubItemResDto;
import com.peoplecore.pay.enums.PayItemType;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 내 급여 조회용 QueryDSL 동적 쿼리 레포지토리
 * - 급여명세서 상세 항목 조회 (지급/공제 분류)
 * - 최근 3개월 급여 합산 (퇴직금 산정용)
 */
@Repository
public class MySalaryQueryRepository {

    @Autowired
    private JPAQueryFactory queryFactory;

    private final QPayrollDetails qDetail = QPayrollDetails.payrollDetails;
    private final QPayItems qItem = QPayItems.payItems;
    private final QPayStubs qStub = QPayStubs.payStubs;
    private final QPayrollRuns qRun = QPayrollRuns.payrollRuns;

    /**
     * 급여명세서 상세 항목 조회 - 지급/공제 항목별 금액
     */
    public List<PayStubItemResDto> findPayStubItems(Long payrollRunId, Long empId, UUID companyId) {
        List<Tuple> results = queryFactory
                .select(qItem.payItemId, qItem.payItemName, qItem.payItemType, qDetail.amount)
                .from(qDetail)
                .join(qItem).on(
                        qDetail.payItemId.eq(qItem.payItemId),
                        qItem.company.companyId.eq(companyId)
                )
                .where(
                        payrollRunIdEq(payrollRunId),
                        empIdEq(empId),
                        detailCompanyEq(companyId)
                )
                .orderBy(qItem.sortOrder.asc())
                .fetch();

        return mapToPayStubItems(results, 0);
    }

    /**
     * 최근 N개월 급여 총액 조회 (퇴직금 산정용)
     */
    public Long sumRecentMonthsPay(Long empId, UUID companyId, List<String> recentMonths) {
        if (recentMonths == null || recentMonths.isEmpty()) {
            return 0L;
        }

        Long total = queryFactory
                .select(qStub.totalPay.sum())
                .from(qStub)
                .where(
                        qStub.empId.eq(empId),
                        qStub.company.companyId.eq(companyId),
                        qStub.payYearMonth.in(recentMonths)
                )
                .fetchOne();

        return total != null ? total : 0L;
    }

    /**
     * 특정 기간 상여금 합산 (퇴직금 산정용)
     * PayItemCategory가 BONUS인 항목의 합산
     */
    public Long sumBonusAmount(Long empId, UUID companyId, List<String> yearMonths) {
        if (yearMonths == null || yearMonths.isEmpty()) {
            return 0L;
        }

        Long total = queryFactory
                .select(qDetail.amount.sum())
                .from(qDetail)
                .join(qItem).on(
                        qDetail.payItemId.eq(qItem.payItemId),
                        qItem.company.companyId.eq(companyId)
                )
                .join(qRun).on(qDetail.payrollRunId.eq(qRun.payrollRunId))
                .where(
                        qDetail.empId.eq(empId),
                        qDetail.company.companyId.eq(companyId),
                        qRun.payYearMonth.in(yearMonths),
                        qItem.payItemCategory.eq(com.peoplecore.pay.enums.PayItemCategory.BONUS)
                )
                .fetchOne();

        return total != null ? total : 0L;
    }

    /**
     * 급여 산정 Run ID 조회 (payYearMonth 기준)
     */
    public Long findPayrollRunId(UUID companyId, String payYearMonth) {
        return queryFactory
                .select(qRun.payrollRunId)
                .from(qRun)
                .where(
                        qRun.company.companyId.eq(companyId),
                        qRun.payYearMonth.eq(payYearMonth)
                )
                .fetchOne();
    }

    // ── BooleanExpression 헬퍼 ──

    private BooleanExpression payrollRunIdEq(Long payrollRunId) {
        return payrollRunId != null ? qDetail.payrollRunId.eq(payrollRunId) : null;
    }

    private BooleanExpression empIdEq(Long empId) {
        return empId != null ? qDetail.empId.eq(empId) : null;
    }

    private BooleanExpression detailCompanyEq(UUID companyId) {
        return companyId != null ? qDetail.company.companyId.eq(companyId) : null;
    }

    // ── 재귀 매핑 ──

    /**
     * Tuple 리스트 → DTO 리스트 변환 (재귀)
     */
    private List<PayStubItemResDto> mapToPayStubItems(List<Tuple> tuples, int index) {
        if (index >= tuples.size()) {
            return Collections.emptyList();
        }

        Tuple tuple = tuples.get(index);
        PayStubItemResDto item = PayStubItemResDto.builder()
                .payItemId(tuple.get(qItem.payItemId))
                .payItemName(tuple.get(qItem.payItemName))
                .payItemType(tuple.get(qItem.payItemType))
                .amount(tuple.get(qDetail.amount))
                .build();

        List<PayStubItemResDto> rest = mapToPayStubItems(tuples, index + 1);

        List<PayStubItemResDto> result = new java.util.ArrayList<>();
        result.add(item);
        result.addAll(rest);
        return result;
    }
}

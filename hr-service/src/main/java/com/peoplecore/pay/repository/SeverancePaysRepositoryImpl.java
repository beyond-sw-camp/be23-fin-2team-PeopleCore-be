package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.QLeaveAllowance;
import com.peoplecore.pay.domain.QPayrollDetails;
import com.peoplecore.pay.domain.QPayrollRuns;
import com.peoplecore.pay.domain.QRetirementPensionDeposits;
import com.peoplecore.pay.enums.DepStatus;
import com.peoplecore.pay.enums.PayItemCategory;
import com.peoplecore.pay.enums.PayItemType;
import com.peoplecore.pay.enums.PayrollStatus;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class SeverancePaysRepositoryImpl implements SeverancePaysRepositoryCustom {
//    퇴직금 산정시 필요한 급여데이터를 PayrollDetails + LeaveAllowance 에서 합산 조회

    private final JPAQueryFactory queryFactory;
    private final QPayrollDetails pd = QPayrollDetails.payrollDetails;
    private final QPayrollRuns pr = QPayrollRuns.payrollRuns;
    private final QLeaveAllowance la = QLeaveAllowance.leaveAllowance;
    private final QRetirementPensionDeposits rpd = QRetirementPensionDeposits.retirementPensionDeposits;

    @Autowired
    public SeverancePaysRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }


//    최근 3개월 급여 총액 (지급항목만)
//    payrollDetails에서 해당 사원 + 해당 월 범위 + PAYMENT타입의 amount 합산 - 공제항목은 제외
    @Override
    public Long sumLast3MonthPay(Long empId, UUID companyId, List<String> months) {

        Long result = queryFactory
                .select(pd.amount.sum())
                .from(pd)
                .join(pd.payrollRuns, pr)
                .where(
                        pd.employee.empId.eq(empId),
                        pd.company.companyId.eq(companyId),
                        pr.payYearMonth.in(months),
                        pd.payItemType.eq(PayItemType.PAYMENT)
                )
                .fetchOne();
        return result != null ? result : 0L;
    }


//    직전 1년 상여금 총액
//    payrollDetails에서 해당 사원 + 12개월(1년) + BONUS 카테고리의 amount 합산
//    payItemCategory.BONUS 로 필터링
//    => 법적으로는 정기/고정적으로 지급되는 상여금만 평균임금에 산입 가능하며,
//    경영/개인성과급 같은 변동성 지급은 제외 대상이 된다.
//    Bonus의 카테고리를 구분해야 맞지만, 프로젝트 특성상 이를 세분화하지않고 단일 카테고리로 관리.
    @Override
    public Long sumLastYearBonus(Long empId, UUID companyId, List<String> months) {
        Long result = queryFactory
                .select(pd.amount.sum())
                .from(pd)
                .join(pd.payrollRuns, pr)
                .where(
                        pd.employee.empId.eq(empId),
                        pd.company.companyId.eq(companyId),
                        pr.payYearMonth.in(months),
                        pd.payItemType.eq(PayItemType.PAYMENT),
                        pd.payItems.payItemCategory.eq(PayItemCategory.BONUS)
                )
                .fetchOne();
        return result != null ? result : 0L;
    }


//    통상임금(월) 조회
//    통상임금 : 고정/일률/정기적으로 지급되는 임금
    @Override
    public Long sumOrdinaryMonthlyPay(Long empId, UUID companyId){
//        최근 확정 급여월 조회
        String latestMonth = queryFactory
                .select(pr.payYearMonth.max())
                .from(pr)
                .where(pr.company.companyId.eq(companyId),
                        pr.payrollStatus.eq(PayrollStatus.CONFIRMED))
                .fetchOne();
        if (latestMonth == null) return 0L;

        Long result =queryFactory
                .select(pd.amount.sum())
                .from(pd)
                .join(pd.payrollRuns, pr)
                .join(pd.payItems)
                .where(
                        pd.employee.empId.eq(empId),
                        pd.company.companyId.eq(companyId),
                        pr.payYearMonth.eq(latestMonth),
                        pd.payItemType.eq(PayItemType.PAYMENT),
                        pd.payItems.isFixed.isTrue(),
                        pd.payItems.payItemCategory.in(
                                PayItemCategory.SALARY,
                                PayItemCategory.ALLOWANCE
                        )
                )
                .fetchOne();
        return result != null ? result : 0L;
    }

    //  3개월급여총액, 1년상여금총액, DC형적립급합계
    @Override
    public Map<Long, Long> sumLast3MonthPayByEmpIds(UUID companyId, List<Long> empIds, List<String> months) {
        List<Tuple> rows = queryFactory
                .select(pd.employee.empId, pd.amount.sum())     // empId별 합계
                .from(pd)
                .join(pd.payrollRuns, pr)
                .where(
                        pd.employee.empId.in(empIds),
                        pd.company.companyId.eq(companyId),
                        pr.payYearMonth.in(months),
                        pd.payItemType.eq(PayItemType.PAYMENT)

                )
                .groupBy(pd.employee.empId)
                .fetch();

        return rows.stream().collect(Collectors.toMap(
                t -> t.get(pd.employee.empId),
                t -> t.get(pd.amount.sum()) != null ? t.get(pd.amount.sum()) : 0L
        ));
    }

    @Override
    public Map<Long, Long> sumLastYearBonusByEmpIds(UUID companyId, List<Long> empIds, List<String> months) {
        List<Tuple> rows = queryFactory
                .select(pd.employee.empId, pd.amount.sum())
                .from(pd)
                .join(pd.payrollRuns, pr)
                .where(
                        pd.employee.empId.in(empIds),
                        pd.company.companyId.eq(companyId),
                        pr.payYearMonth.in(months),
                        pd.payItemType.eq(PayItemType.PAYMENT),
                        pd.payItems.payItemCategory.eq(PayItemCategory.BONUS)
                )
                .groupBy(pd.employee.empId)
                .fetch();

        return rows.stream().collect(Collectors.toMap(
                t -> t.get(pd.employee.empId),
                t -> t.get(pd.amount.sum()) != null ? t.get(pd.amount.sum()) : 0L
        ));
    }

    @Override
    public Map<Long, Long> sumDcDepositedTotalByEmpIds(UUID companyId, List<Long> empIds) {
        List<Tuple> rows = queryFactory
                .select(rpd.employee.empId, rpd.depositAmount.sum())
                .from(rpd)
                .where(
                        rpd.employee.empId.in(empIds),
                        rpd.company.companyId.eq(companyId),
                        rpd.depStatus.eq(DepStatus.COMPLETED)
                )
                .groupBy(rpd.employee.empId)
                .fetch();

        return rows.stream().collect(java.util.stream.Collectors.toMap(
                t -> t.get(rpd.employee.empId),
                t -> {
                    Long v = t.get(rpd.depositAmount.sum());
                    return v != null ? v : 0L;
                }
        ));
    }

//    직전 N개월 PAYMENT 항목들의 비과세 누계
    @Override
    public Long sumNonTaxableLast3Months(Long empId, UUID companyId, List<String> months) {

        // PayrollDetails + PayItems 조인해 PAYMENT 항목 raw 데이터 가져온 뒤
        // 자바에서 항목별 정책에 맞춰 min(amount, taxExemptLimit) / 전액비과세 분기.
        List<Tuple> rows = queryFactory
                .select(pd.amount, pd.payItems.isTaxable, pd.payItems.taxExemptLimit)
                .from(pd)
                .join(pd.payrollRuns, pr)
                .join(pd.payItems)
                .where(
                        pd.employee.empId.eq(empId),
                        pd.company.companyId.eq(companyId),
                        pr.payYearMonth.in(months),
                        pd.payItemType.eq(PayItemType.PAYMENT)
                )
                .fetch();

        long sum = 0L;
        for (Tuple t : rows) {
            Long amt = t.get(pd.amount);
            Boolean taxable = t.get(pd.payItems.isTaxable);
            Integer cap = t.get(pd.payItems.taxExemptLimit);
            if (amt == null) continue;
            long a = amt;
            if (Boolean.FALSE.equals(taxable)) {
                sum += a;                                  // 전액 비과세
            } else {
                int c = (cap == null) ? 0 : cap;
                sum += Math.min(a, c);                     // 한도까지만 비과세
            }
        }
        return sum;
    }


//    DB형 기적립금 합계
//    RetirementPensionDeposits에서 COMPLETED 상태의 depositAmount합산
    @Override
    public Long sumDcDepositedTotal(Long empId, UUID companyId) {
        Long result = queryFactory
                .select(rpd.depositAmount.sum())
                .from(rpd)
                .where(
                        rpd.employee.empId.eq(empId),
                        rpd.company.companyId.eq(companyId),
                        rpd.depStatus.eq(DepStatus.COMPLETED)
                )
                .fetchOne();
        return result != null ? result : 0L;
    }
}

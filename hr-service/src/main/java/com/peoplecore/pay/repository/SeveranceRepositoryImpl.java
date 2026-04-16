package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.QLeaveAllowance;
import com.peoplecore.pay.domain.QPayrollDetails;
import com.peoplecore.pay.domain.QPayrollRuns;
import com.peoplecore.pay.domain.QRetirementPensionDeposits;
import com.peoplecore.pay.enums.DepStatus;
import com.peoplecore.pay.enums.PayItemCategory;
import com.peoplecore.pay.enums.PayItemType;
import com.peoplecore.pay.enums.PayrollStatus;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class SeveranceRepositoryImpl implements SeveranceRepositoryCustom {
//    퇴직금 산정시 필요한 급여데이터를 PayrollDetails + LeaveAllowance 에서 합산 조회

    private final JPAQueryFactory queryFactory;
    private final QPayrollDetails pd = QPayrollDetails.payrollDetails;
    private final QPayrollRuns pr = QPayrollRuns.payrollRuns;
    private final QLeaveAllowance la = QLeaveAllowance.leaveAllowance;
    private final QRetirementPensionDeposits rpd = QRetirementPensionDeposits.retirementPensionDeposits;

    @Autowired
    public SeveranceRepositoryImpl(JPAQueryFactory queryFactory) {
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

    //    연차수당 조회 //TODO : 삭제??
//    LeaveAllowance에서 해당 사원의 해당 연도 산정금액
//    퇴직시
    @Override
    public Long getAnnualLeaveAllowance(Long empId, UUID companyId, int year) {
        Long result = queryFactory
                .select(la.allowanceAmount)
                .from(la)
                .where(
                        la.employee.empId.eq(empId),
                        la.company.companyId.eq(companyId),
                        la.year.eq(year)
                )
                .fetchOne();
        return result != null ? result : 0L;
    }

//    통상임금(월) 조회
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

//    DB형 기적립금 합계
//    retirementPensionDeposits에서 COMPLETED 상태의 depositAmount합산
    @Override
    public Long sumDcDepositedTotal(Long empId, UUID companyId) {
        Long result = queryFactory
                .select(rpd.depositAmount.sum())
                .from(rpd)
                .where(
                        rpd.empId.eq(empId),
                        rpd.company.companyId.eq(companyId),
                        rpd.depStatus.eq(DepStatus.COMPLETED)
                )
                .fetchOne();
        return result != null ? result : 0L;
    }
}

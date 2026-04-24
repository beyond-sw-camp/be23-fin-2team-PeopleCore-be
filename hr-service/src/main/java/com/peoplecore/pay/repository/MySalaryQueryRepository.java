package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.QPayItems;
import com.peoplecore.pay.domain.QPayStubs;
import com.peoplecore.pay.domain.QPayrollDetails;
import com.peoplecore.pay.dtos.PayStubItemResDto;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MySalaryQueryRepository {

    private final JPAQueryFactory queryFactory;

    public MySalaryQueryRepository(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }


    /**
     * 특정 PayStub 의 개별 항목 조회.
     * PayStubs.payrollRunId + PayStubs.empId 로 PayrollDetails 매칭 후 PayItems 조인.
     */
    public List<PayStubItemResDto> findPayStubItems(Long payStubsId) {
        QPayStubs stub = QPayStubs.payStubs;
        QPayrollDetails detail = QPayrollDetails.payrollDetails;
        QPayItems item = QPayItems.payItems;

        return queryFactory
                .select(Projections.constructor(
                        PayStubItemResDto.class,
                        item.payItemId,
                        item.payItemName,
                        item.payItemType.stringValue(),
                        item.payItemCategory.stringValue(),
                        detail.amount,
                        item.isTaxable
                ))
                .from(stub)
                .join(detail).on(
                        detail.payrollRuns.payrollRunId.eq(stub.payrollRunId),
                        detail.employee.empId.eq(stub.empId)
                )
                .join(item).on(detail.payItems.payItemId.eq(item.payItemId))
                .where(stub.payStubsId.eq(payStubsId))
                .orderBy(item.sortOrder.asc())
                .fetch();
    }
}

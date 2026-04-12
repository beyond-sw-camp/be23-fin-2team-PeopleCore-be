package com.peoplecore.hrorder.repository;

import com.peoplecore.employee.domain.QEmployee;
import com.peoplecore.hrorder.domain.HrOrder;
import com.peoplecore.hrorder.domain.OrderStatus;
import com.peoplecore.hrorder.domain.OrderType;
import com.peoplecore.hrorder.domain.QHrOrder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public class HrOrderRepositoryImpl implements HrOrderRepositoryCustom{

    private  final JPAQueryFactory queryFactory;
    private final QHrOrder qOrder= QHrOrder.hrOrder;
    private final QEmployee qEmployee = QEmployee.employee;

    public HrOrderRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }


    @Override
    public Page<HrOrder> findAllWithFilter(UUID companyId, String keyword, OrderType orderType, OrderStatus status, Pageable pageable){

//        데이터 조회(employee fetchJoin)
        List<HrOrder>content = queryFactory
                .selectFrom(qOrder)
                .join(qOrder.employee,qEmployee).fetchJoin() //사원정보 한번에 조회
                .where(
                        companyEq(companyId), //회사 필터
                        orderTypeEq(orderType), //발령유형 필터
                        statusEq(status),
                        keywordContains(keyword),
                        qOrder.deletedAt.isNull()
                )
                .orderBy(qOrder.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
//        전체건수 조회
        Long total = queryFactory
                .select(qOrder.count())
                .from(qOrder)
                .join(qOrder.employee,qEmployee)
                .where(
                        companyEq(companyId),
                        orderTypeEq(orderType),
                        statusEq(status),
                        keywordContains(keyword),
                        qOrder.deletedAt.isNull()
                )
                .fetchOne();
    return new PageImpl<>(content, pageable,total != null ? total : 0L);
    }

//    회사 필터
    private BooleanExpression companyEq(UUID companyId){
        return companyId != null ? qEmployee.company.companyId.eq(companyId) : null;
    }

//    발령유형필터
    private BooleanExpression orderTypeEq(OrderType orderType){
        return orderType != null ? qOrder.orderType.eq(orderType) : null;
    }
//    상태필터
    private BooleanExpression statusEq(OrderStatus status) {
        return status != null ? qOrder.status.eq(status) : null;
    }
//    keyword포함여부
    private BooleanExpression keywordContains(String keyword){
        return keyword != null ? qEmployee.empName.contains(keyword).or(qEmployee.empNum.contains(keyword)): null;
    }
}

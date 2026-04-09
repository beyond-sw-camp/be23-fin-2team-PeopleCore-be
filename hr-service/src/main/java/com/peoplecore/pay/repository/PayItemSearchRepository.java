package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.PayItems;
import com.peoplecore.pay.domain.QPayItems;
import com.peoplecore.pay.enums.PayItemType;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class PayItemSearchRepository {

    private final JPAQueryFactory queryFactory;
    private final QPayItems payItems = QPayItems.payItems;

    @Autowired
    public PayItemSearchRepository(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public List<PayItems> search(UUID companyId, PayItemType type, String name, boolean isLegal){
        return queryFactory
                .selectFrom(payItems)
                .where(
                        companyIdEq(companyId),     //필수조건
                        typeEq(type),               //필수조건
                        nameContains(name),         //동적조건 (null일수있음)
                        isLegalEq(isLegal)          //동적조건
                )
                .orderBy(payItems.sortOrder.asc())
                .fetch();
    }

    private BooleanExpression companyIdEq(UUID companyId){
        return payItems.company.companyId.eq(companyId);
    }

    private BooleanExpression typeEq(PayItemType type){
        return payItems.payItemType.eq(type);
    }

    private BooleanExpression nameContains(String name){
        return (name != null && !name.isBlank()) ? payItems.payItemName.contains(name) : null;
    }

    private BooleanExpression isLegalEq(Boolean isLegal){
        return isLegal != null ? payItems.isLegal.eq(isLegal) : null;
    }
}

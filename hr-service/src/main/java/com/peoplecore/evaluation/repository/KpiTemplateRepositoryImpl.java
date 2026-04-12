package com.peoplecore.evaluation.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

// KPI지표 QueryDSL 구현체
@Repository
@RequiredArgsConstructor
public class KpiTemplateRepositoryImpl implements KpiTemplateRepositoryCustom {
    private final JPAQueryFactory queryFactory;
}

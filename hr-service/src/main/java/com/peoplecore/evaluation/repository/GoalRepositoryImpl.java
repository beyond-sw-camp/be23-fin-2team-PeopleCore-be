package com.peoplecore.evaluation.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

// 목표 QueryDSL 구현체
@Repository
@RequiredArgsConstructor
public class GoalRepositoryImpl implements GoalRepositoryCustom {
    private final JPAQueryFactory queryFactory;
}

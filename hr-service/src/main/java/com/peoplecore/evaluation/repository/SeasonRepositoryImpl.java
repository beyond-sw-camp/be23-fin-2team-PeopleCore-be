package com.peoplecore.evaluation.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

// 평가시즌 QueryDSL 구현체
@Repository
@RequiredArgsConstructor
public class SeasonRepositoryImpl implements SeasonRepositoryCustom {
    private final JPAQueryFactory queryFactory;
}

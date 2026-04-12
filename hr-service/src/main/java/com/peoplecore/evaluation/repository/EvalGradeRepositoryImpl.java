package com.peoplecore.evaluation.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

// 등급 QueryDSL 구현체
@Repository
@RequiredArgsConstructor
public class EvalGradeRepositoryImpl implements EvalGradeRepositoryCustom {
    private final JPAQueryFactory queryFactory;
}

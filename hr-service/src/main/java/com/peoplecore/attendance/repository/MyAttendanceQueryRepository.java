package com.peoplecore.attendance.repository;

import com.peoplecore.attendance.dto.WeeklyCommuteAggregate;
import com.peoplecore.attendance.entity.QCommuteRecord;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

/*
 * 사원 개인 주간요약 API 전용 QueryDSL Repository.
 * 단일 쿼리로 4개 집계 동시 계산:
 *  - 실근무 분 (자동마감/미체크아웃 제외)
 *  - 출근 일수
 *  - 인정 초과 분 (extended + night + holiday)
 *  - 자동마감 일수
 * 파티션 프루닝: WHERE work_date BETWEEN ... — 최대 2개 월별 파티션만 스캔.
 */
@Repository
public class MyAttendanceQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Autowired
    public MyAttendanceQueryRepository(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    /**
     * 주간 통합 집계.
     */
    public WeeklyCommuteAggregate aggregateWeeklyStats(UUID companyId, Long empId,
                                                       LocalDate from, LocalDate to) {
        QCommuteRecord c = QCommuteRecord.commuteRecord;

        // 실근무 분: TIMESTAMPDIFF + 가드 (자동마감 / 미체크아웃 제외)
        // JPQL 표준에 TIMESTAMPDIFF 없어서 native function template 사용.
        NumberExpression<Long> workedSum = Expressions.numberTemplate(Long.class,
                "COALESCE(SUM(CASE WHEN {0} IS NOT NULL AND {1} IS NOT NULL AND {2} = FALSE " +
                        "THEN TIMESTAMPDIFF(MINUTE, {0}, {1}) ELSE 0 END), 0)",
                c.comRecCheckIn, c.comRecCheckOut, c.isAutoClosed);

        // 출근 일수: COUNT(DISTINCT workDate WHERE checkIn IS NOT NULL)
        NumberExpression<Long> attendedDays = Expressions.numberTemplate(Long.class,
                "COUNT(DISTINCT CASE WHEN {0} IS NOT NULL THEN {1} END)",
                c.comRecCheckIn, c.workDate);

        // 인정 초과 합: extended + night + holiday
        NumberExpression<Long> recognizedSum = Expressions.numberTemplate(Long.class,
                "COALESCE(SUM({0} + {1} + {2}), 0)",
                c.recognizedExtendedMinutes,
                c.recognizedNightMinutes,
                c.recognizedHolidayMinutes);

        // 자동마감 일수
        NumberExpression<Long> autoClosedDays = Expressions.numberTemplate(Long.class,
                "COALESCE(SUM(CASE WHEN {0} = TRUE THEN 1 ELSE 0 END), 0)",
                c.isAutoClosed);

        return queryFactory
                .select(Projections.constructor(WeeklyCommuteAggregate.class,
                        workedSum, attendedDays, recognizedSum, autoClosedDays))
                .from(c)
                .where(c.companyId.eq(companyId)
                        .and(c.employee.empId.eq(empId))
                        .and(c.workDate.between(from, to)))
                .fetchOne();
    }
}
package com.peoplecore.attendance.repository;

import com.peoplecore.attendance.dto.WeeklyCommuteAggregate;
import com.peoplecore.attendance.entity.CommuteRecord;
import com.peoplecore.attendance.entity.QCommuteRecord;
import com.peoplecore.attendance.entity.WorkStatus;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.domain.QEmployee;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/*
 * 사원 개인 주간요약 API 전용 QueryDSL Repository.
 * 단일 쿼리로 4개 집계 동시 계산:
 *  - 실근무 분 (자동마감/결근 제외)
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
     * AUTO_CLOSED/ABSENT 행은 실근무 분에서 제외.
     */
    public WeeklyCommuteAggregate aggregateWeeklyStats(UUID companyId, Long empId,
                                                       LocalDate from, LocalDate to) {
        QCommuteRecord c = QCommuteRecord.commuteRecord;

        // 실근무 분: AUTO_CLOSED/ABSENT 제외 + 체크인/아웃 모두 있을 때만 합산
        NumberExpression<Long> workedSum = Expressions.numberTemplate(Long.class,
                "COALESCE(SUM(CASE WHEN {0} IS NOT NULL AND {1} IS NOT NULL" +
                        " AND {2} != 'AUTO_CLOSED' AND {2} != 'ABSENT'" +
                        " THEN TIMESTAMPDIFF(MINUTE, {0}, {1}) ELSE 0 END), 0)",
                c.comRecCheckIn, c.comRecCheckOut, c.workStatus);

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
                "COALESCE(SUM(CASE WHEN {0} = 'AUTO_CLOSED' THEN 1 ELSE 0 END), 0)",
                c.workStatus);

        return queryFactory
                .select(Projections.constructor(WeeklyCommuteAggregate.class,
                        workedSum, attendedDays, recognizedSum, autoClosedDays))
                .from(c)
                .where(c.companyId.eq(companyId)
                        .and(c.employee.empId.eq(empId))
                        .and(c.workDate.between(from, to)))
                .fetchOne();
    }

    /**
     * 월간 지각 발생일 조회.
     * 조건: company + emp + workDate BETWEEN + workStatus IN (LATE, LATE_AND_EARLY) + checkIn NOT NULL
     * 정렬: workDate ASC
     * 파티션 프루닝: workDate 범위 조건으로 해당 월 파티션만 스캔.
     * 사용: 월간 요약의 lateDays[] 채움. lateMinutes 는 service 에서 groupStartTime 으로 계산.
     */
    public List<CommuteRecord> findMonthlyLateRecords(UUID companyId, Long empId,
                                                      LocalDate from, LocalDate to) {
        QCommuteRecord c = QCommuteRecord.commuteRecord;
        return queryFactory
                .selectFrom(c)
                .where(c.companyId.eq(companyId)
                        .and(c.employee.empId.eq(empId))
                        .and(c.workDate.between(from, to))
                        .and(c.workStatus.in(WorkStatus.LATE, WorkStatus.LATE_AND_EARLY))
                        .and(c.comRecCheckIn.isNotNull()))
                .orderBy(c.workDate.asc())
                .fetch();
    }

    /**
     * 월간 초과근무 발생일 조회.
     * 조건: company + emp + workDate BETWEEN + (recognizedExtended + recognizedNight + recognizedHoliday) > 0 + checkOut NOT NULL
     * 정렬: workDate ASC
     * 파티션 프루닝: workDate 범위 조건.
     * 사용: 월간 요약의 overtimeDays[] 채움. overtimeStartAt/approvedOvertimeMinutes 는 service 에서 산출.
     */
    public List<CommuteRecord> findMonthlyOvertimeRecords(UUID companyId, Long empId,
                                                          LocalDate from, LocalDate to) {
        QCommuteRecord c = QCommuteRecord.commuteRecord;
        return queryFactory
                .selectFrom(c)
                .where(c.companyId.eq(companyId)
                        .and(c.employee.empId.eq(empId))
                        .and(c.workDate.between(from, to))
                        .and(c.comRecCheckOut.isNotNull())
                        .and(c.recognizedExtendedMinutes
                                .add(c.recognizedNightMinutes)
                                .add(c.recognizedHolidayMinutes).gt(0L)))
                .orderBy(c.workDate.asc())
                .fetch();
    }

    /**
     * 특정 근무그룹 + 특정 날짜의 자동마감 대상 조회.
     * 조건: checkIn 있음 + checkOut 없음 + 해당 wg 소속.
     * AUTO_CLOSED 레코드는 checkOut 이 세팅되므로 별도 조건 불필요.
     */
    public List<CommuteRecord> findAutoCloseTargets(UUID companyId, Long workGroupId, LocalDate targetDate) {
        QCommuteRecord c = QCommuteRecord.commuteRecord;
        QEmployee e = QEmployee.employee;

        return queryFactory
                .selectFrom(c)
                .join(c.employee, e).fetchJoin()
                .where(c.companyId.eq(companyId)
                        .and(c.workDate.eq(targetDate))
                        .and(c.comRecCheckIn.isNotNull())
                        .and(c.comRecCheckOut.isNull())
                        .and(e.workGroup.workGroupId.eq(workGroupId)))
                .fetch();
    }

    /**
     * 특정 근무그룹 + 특정 날짜의 결근 대상 사원 조회.
     * 조건: 해당 wg 소속 + 퇴사 아님 + 해당 날짜 CommuteRecord(어떤 상태든) 없음.
     * 호출부에서 해당 날짜가 wg 소정근무요일인지 확인 후 호출할 것.
     */
    public List<Employee> findAbsentTargets(UUID companyId, Long workGroupId, LocalDate targetDate) {
        QEmployee e = QEmployee.employee;
        QCommuteRecord cr = QCommuteRecord.commuteRecord;

        return queryFactory
                .selectFrom(e)
                .where(
                        e.company.companyId.eq(companyId),
                        e.workGroup.workGroupId.eq(workGroupId),
                        e.empStatus.ne(EmpStatus.RESIGNED),
                        // 해당 날짜 CommuteRecord 없음 (결근 배치 중복 실행 방지 포함)
                        JPAExpressions.selectOne()
                                .from(cr)
                                .where(cr.employee.empId.eq(e.empId)
                                        .and(cr.workDate.eq(targetDate)))
                                .notExists()
                )
                .fetch();
    }
}

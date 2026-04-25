package com.peoplecore.attendance.repository;

import com.peoplecore.attendance.dto.WeekCommuteRow;
import com.peoplecore.attendance.dto.WeekEmpRow;
import com.peoplecore.attendance.dto.WeekVacationRow;
import com.peoplecore.attendance.entity.EmploymentFilter;
import com.peoplecore.attendance.entity.QCommuteRecord;
import com.peoplecore.attendance.entity.QWorkGroup;
import com.peoplecore.attendance.entity.WorkStatus;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.QEmployee;
import com.peoplecore.vacation.entity.QVacationRequest;
import com.peoplecore.vacation.entity.RequestStatus;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/* 집계 탭 전용 queryDsl repo
 *
 *  DB성능을  위해 일자별로 7회 호출이 아닌 주단위 3회 쿼리로 일괄 조회
 * fetchEmployee  : 회사 + 재직필터 + 미퇴사 사원 + 근무 그룹 비트마스크
 * fetchCommuteInWeek : 주 범위 파티션 프루닝
 * fetchApprovedVacationInWeek : 주와 겹치는 휴가 승인 구간 */
@Repository
public class AttendanceAggregateQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Autowired
    public AttendanceAggregateQueryRepository(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    /* 1차 쿼리  */
    public List<WeekEmpRow> fetchEmployees(UUID companyId, EmploymentFilter filter) {
        QEmployee e = QEmployee.employee;
        QWorkGroup wg = QWorkGroup.workGroup;

        /* 필터 조립 -> 회사 + 재직 상태 + 퇴사 X */
        BooleanBuilder where = new BooleanBuilder();
        where.and(e.company.companyId.eq(companyId));
        where.and(e.empStatus.in(filter.getAllowedStatuses()));
        where.and(e.empStatus.ne(EmpStatus.RESIGNED));

        /*근무 그룹은 미배정 가능성 있어 left Join*/
        return queryFactory.select(Projections.fields(WeekEmpRow.class, e.empId, wg.groupWorkDay)).from(e).leftJoin(e.workGroup, wg).where(where).fetch();
    }

    /*2차 쿼리 */
    public List<WeekCommuteRow> fetchCommutesInWeek(UUID companyId, List<Long> empIds, LocalDate weekStart, LocalDate weekEnd) {
        if (empIds == null || empIds.isEmpty()) return List.of();
        QCommuteRecord cr = QCommuteRecord.commuteRecord;

        /*mysql에서만 가능 ->  TIMESTAMPDIFF로 체크아웃 전 null 유지   */
        NumberExpression<Long> minutes = Expressions.numberTemplate(Long.class, "CASE WHEN {0} IS NULL THEN NULL ELSE TIMESTAMPDIFF(MINUTE, {1}, {0}) END",
                cr.comRecCheckOut, cr.comRecCheckIn);

        return queryFactory.select(Projections.fields(WeekCommuteRow.class, cr.employee.empId.as("empId"), cr.workDate, cr.workStatus, minutes.as("minutes"))).from(cr).where(cr.companyId.eq(companyId), cr.employee.empId.in(empIds), cr.workDate.between(weekStart, weekEnd)).fetch();
    }

    /*3차 쿼리 */
    public List<WeekVacationRow> fetApprovedVacationInWeek(UUID companyId, List<Long> empIds, LocalDate weekStart, LocalDate weekEnd) {
        if (empIds == null || empIds.isEmpty()) return List.of();

        QVacationRequest vr = QVacationRequest.vacationRequest;

        LocalDateTime weekStartAt = weekStart.atStartOfDay();
        LocalDateTime weekEndAt = weekEnd.atTime(LocalTime.MAX);

        return queryFactory.select(Projections.fields(WeekVacationRow.class, vr.employee.empId.as("empId"), vr.requestStartAt.as("startAt"), vr.requestEndAt.as("endAt"), vr.requestUseDays.as("vacReqUseDay"))).from(vr).where(vr.companyId.eq(companyId), vr.employee.empId.in(empIds), vr.requestStatus.eq(RequestStatus.APPROVED), vr.requestStartAt.loe(weekEndAt), vr.requestEndAt.goe(weekStartAt)).fetch();

    }

}
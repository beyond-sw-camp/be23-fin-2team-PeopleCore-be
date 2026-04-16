package com.peoplecore.attendance.repository;

import com.peoplecore.attendance.dto.AttendanceAdminRow;
import com.peoplecore.attendance.entity.*;
import com.peoplecore.department.domain.QDepartment;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.QEmployee;
import com.peoplecore.grade.domain.QGrade;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import com.peoplecore.vacation.entity.QVacationRequest;
import com.peoplecore.vacation.entity.QVacationType;
import com.peoplecore.vacation.entity.RequestStatus;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/*
 * 근태 현황 관리자 API 전용 QueryDSL Repository.
 * 쿼리 구성 (4쿼리 — empId IN 절로 N+1 없이 일괄 조회):
 *  1) fetchBaseRows: Employee + Dept + Grade + WorkGroup + 오늘 CommuteRecord
 *  2) fetchApprovedVacationMap: empId → (vacTypeName)
 *  3) fetchApprovedOtMinutesMap: empId → 분 합계
 *  4) fetchWeekWorkedMinutesMap: empId → 주간 분 합계

 * 파티션 프루닝:
 *  - 메인 cr.workDate = :date (ON 절)
 *  - 주간 cr2.workDate BETWEEN weekStart AND weekEnd (최대 2개 파티션)
 */
@Repository
public class AttendanceAdminQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Autowired
    public AttendanceAdminQueryRepository(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    /**
     * Summary API 용 오버로드 — 필터 없음.
     */
    public List<AttendanceAdminRow> fetchAll(UUID companyId, LocalDate date, EmploymentFilter filter) {
        return fetchAll(companyId, date, filter, null, null, null);
    }

    /**
     * List API 용 — 부서/근무그룹/검색어 필터 포함.
     */
    public List<AttendanceAdminRow> fetchAll(UUID companyId, LocalDate date, EmploymentFilter filter,
                                             Long deptId, Long workGroupId, String keyword) {
        List<AttendanceAdminRow> rows = fetchBaseRows(companyId, date, filter, deptId, workGroupId, keyword);
        if (rows.isEmpty()) return rows;

        List<Long> empIds = rows.stream().map(AttendanceAdminRow::getEmpId).toList();

        Map<Long, String> vacMap = fetchApprovedVacationMap(empIds, date);
        Map<Long, Long> otMap = fetchApprovedOtMinutesMap(empIds, date);
        Map<Long, Long> weekMap = fetchWeekWorkedMinutesMap(empIds, date);

        for (AttendanceAdminRow r : rows) {
            String vacName = vacMap.get(r.getEmpId());
            r.setHasApprovedVacationToday(vacName != null);
            r.setVacationTypeName(vacName);
            r.setApprovedOtMinutesToday(otMap.getOrDefault(r.getEmpId(), 0L));
            r.setWeekWorkedMinutes(weekMap.getOrDefault(r.getEmpId(), 0L));
        }
        return rows;
    }

    /*
     * 1차 쿼리 — Employee 중심 JOIN + 선택적 필터.
     */
    private List<AttendanceAdminRow> fetchBaseRows(UUID companyId, LocalDate date, EmploymentFilter filter,
                                                   Long deptId, Long workGroupId, String keyword) {
        QEmployee e = QEmployee.employee;
        QDepartment d = QDepartment.department;
        QGrade g = QGrade.grade;
        QWorkGroup wg = QWorkGroup.workGroup;
        QCommuteRecord cr = QCommuteRecord.commuteRecord;

        BooleanBuilder where = new BooleanBuilder();
        where.and(e.company.companyId.eq(companyId));
        where.and(e.empStatus.in(filter.getAllowedStatuses()));
        where.and(e.empStatus.ne(EmpStatus.RESIGNED));
        if (deptId != null) where.and(d.deptId.eq(deptId));
        if (workGroupId != null) where.and(wg.workGroupId.eq(workGroupId));
        if (keyword != null && !keyword.isBlank()) {
            String kw = "%" + keyword.trim() + "%";
            where.and(
                    e.empNum.like(kw)
                            .or(e.empName.like(kw))
                            .or(d.deptName.like(kw))
            );
        }

        return queryFactory
                .select(Projections.fields(AttendanceAdminRow.class,
                        e.empId,
                        e.empNum,
                        e.empName,
                        e.empStatus,
                        d.deptId,
                        d.deptName,
                        g.gradeId,
                        g.gradeName,
                        wg.workGroupId,
                        wg.groupName.as("workGroupName"),
                        wg.groupStartTime,
                        wg.groupEndTime,
                        wg.groupWorkDay,
                        cr.comRecId,
                        cr.comRecCheckIn.as("checkInAt"),
                        cr.comRecCheckOut.as("checkOutAt"),
                        cr.isOffsite,
                        cr.checkInIp,               // OFFSITE 카드 drilldown 에서 IP 노출용
                        cr.checkInStatus,
                        cr.checkOutStatus,
                        cr.holidayReason
                ))
                .from(e)
                .innerJoin(e.dept, d)
                .innerJoin(e.grade, g)
                .leftJoin(e.workGroup, wg)
                .leftJoin(cr).on(
                        cr.employee.eq(e),
                        cr.workDate.eq(date)
                )
                .where(where)
                .fetch();
    }

    /* 사원별 오늘 승인 휴가 유형명 맵 - empId IN 절로 일괄 조회 */
    private Map<Long, String> fetchApprovedVacationMap(List<Long> empIds, LocalDate date) {
        QVacationRequest vr = QVacationRequest.vacationRequest;
        QVacationType vt = QVacationType.vacationType;
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        /* @ManyToOne 매핑이라 ON 절 없이 직접 join */
        List<Tuple> list = queryFactory
                .select(vr.employee.empId, vt.typeName)
                .from(vr)
                .leftJoin(vr.vacationType, vt)
                .where(
                        vr.employee.empId.in(empIds),
                        vr.requestStatus.eq(RequestStatus.APPROVED),
                        vr.requestStartAt.loe(endOfDay),
                        vr.requestEndAt.goe(startOfDay)
                )
                .fetch();

        Map<Long, String> result = new HashMap<>();
        for (Tuple t : list) {
            result.put(t.get(vr.employee.empId), t.get(vt.typeName));
        }
        return result;
    }

    private Map<Long, Long> fetchApprovedOtMinutesMap(List<Long> empIds, LocalDate date) {
        QOvertimeRequest ot = QOvertimeRequest.overtimeRequest;
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        NumberExpression<Long> minutes = Expressions.numberTemplate(Long.class,
                "TIMESTAMPDIFF(MINUTE, {0}, {1})", ot.otPlanStart, ot.otPlanEnd);

        List<Tuple> list = queryFactory
                .select(ot.employee.empId, minutes.sum())
                .from(ot)
                .where(
                        ot.employee.empId.in(empIds),
                        ot.otStatus.eq(OtStatus.APPROVED),
                        ot.otDate.between(startOfDay, endOfDay)
                )
                .groupBy(ot.employee.empId)
                .fetch();

        Map<Long, Long> result = new HashMap<>();
        for (Tuple t : list) {
            Long m = t.get(minutes.sum());
            result.put(t.get(ot.employee.empId), m != null ? m : 0L);
        }
        return result;
    }

    private Map<Long, Long> fetchWeekWorkedMinutesMap(List<Long> empIds, LocalDate date) {
        QCommuteRecord cr = QCommuteRecord.commuteRecord;
        LocalDate weekStart = date.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = date.with(DayOfWeek.SUNDAY);

        NumberExpression<Long> minutes = Expressions.numberTemplate(Long.class,
                "TIMESTAMPDIFF(MINUTE, {0}, {1})", cr.comRecCheckIn, cr.comRecCheckOut);

        List<Tuple> list = queryFactory
                .select(cr.employee.empId, minutes.sum())
                .from(cr)
                .where(
                        cr.employee.empId.in(empIds),
                        cr.workDate.between(weekStart, weekEnd),
                        cr.comRecCheckOut.isNotNull()
                )
                .groupBy(cr.employee.empId)
                .fetch();

        Map<Long, Long> result = new HashMap<>();
        for (Tuple t : list) {
            Long m = t.get(minutes.sum());
            result.put(t.get(cr.employee.empId), m != null ? m : 0L);
        }
        return result;
    }
}
package com.peoplecore.pay.repository;

import com.peoplecore.employee.domain.QEmployee;
import com.peoplecore.pay.domain.QRetirementPensionDeposits;
import com.peoplecore.pay.dtos.MonthlyDepositSummaryDto;
import com.peoplecore.pay.dtos.PensionDepositByEmployeeResDto;
import com.peoplecore.pay.dtos.PensionDepositResDto;
import com.peoplecore.pay.enums.DepStatus;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public class PensionDepositQueryRepositoryImpl implements PensionDepositQueryRepository {

    private final JPAQueryFactory queryFactory;
    private final QRetirementPensionDeposits rpd = QRetirementPensionDeposits.retirementPensionDeposits;
    private final QEmployee qEmp = QEmployee.employee;

    @Autowired
    public PensionDepositQueryRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    // ── 1. 페이징 목록 조회 ──
    @Override
    public Page<PensionDepositResDto> search(UUID companyId, String fromYm, String toYm,
                                             Long empId, Long deptId, DepStatus status,
                                             Pageable pageable) {
        BooleanBuilder where = buildWhere(companyId, fromYm, toYm, empId, status);
        if (deptId != null) where.and(rpd.employee.dept.deptId.eq(deptId));

        List<PensionDepositResDto> content = queryFactory
                .select(Projections.constructor(PensionDepositResDto.class,
                        rpd.depId,
                        rpd.employee.empId,
                        rpd.employee.empName,
                        rpd.employee.dept.deptName,
                        rpd.payYearMonth,
                        rpd.baseAmount,
                        rpd.depositAmount,
                        rpd.depStatus.stringValue(),
                        rpd.depositDate,
                        rpd.payrollRun.payrollRunId,
                        rpd.isManual
                ))
                .from(rpd)
                .join(rpd.employee, qEmp)
                .where(where)
                .orderBy(rpd.depositDate.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(rpd.count())
                .from(rpd)
                .join(rpd.employee, qEmp)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0);
    }

    // ── 2. 적립 총액 ──
    @Override
    public Long sumDepositAmount(UUID companyId, String fromYm, String toYm, DepStatus status) {
        Long result = queryFactory
                .select(rpd.depositAmount.sum())
                .from(rpd)
                .where(buildWhere(companyId, fromYm, toYm, null, status))
                .fetchOne();
        return result != null ? result : 0L;
    }

    // ── 3. 고유 사원 수 ──
    @Override
    public Integer countDistinctEmployees(UUID companyId, String fromYm, String toYm, DepStatus status) {
        Long count = queryFactory
                .select(rpd.employee.empId.countDistinct())
                .from(rpd)
                .where(buildWhere(companyId, fromYm, toYm, null, status))
                .fetchOne();
        return count != null ? count.intValue() : 0;
    }

    // ── 4. 회사 전체 누적 적립 (기간 무관, COMPLETED만) ──
    @Override
    public Long grandTotalDeposited(UUID companyId) {
        Long result = queryFactory
                .select(rpd.depositAmount.sum())
                .from(rpd)
                .where(
                        rpd.company.companyId.eq(companyId),
                        rpd.depStatus.eq(DepStatus.COMPLETED)
                )
                .fetchOne();
        return result != null ? result : 0L;
    }

    // ── 5. 사원별 월별 이력 ──
    @Override
    public List<PensionDepositResDto> findByEmpId(UUID companyId, Long empId, String fromYm, String toYm) {
        return queryFactory
                .select(Projections.constructor(PensionDepositResDto.class,
                        rpd.depId,
                        rpd.employee.empId,
                        rpd.employee.empName,
                        rpd.employee.dept.deptName,
                        rpd.payYearMonth,
                        rpd.baseAmount,
                        rpd.depositAmount,
                        rpd.depStatus.stringValue(),
                        rpd.depositDate,
                        rpd.payrollRun.payrollRunId,
                        rpd.isManual
                ))
                .from(rpd)
                .join(rpd.employee, qEmp)
                .where(buildWhere(companyId, fromYm, toYm, empId, null))
                .orderBy(rpd.payYearMonth.desc())
                .fetch();
    }

    // ── 6. 월별 요약 (차트용) ──
    @Override
    public List<MonthlyDepositSummaryDto> monthlySummary(UUID companyId, Integer year) {
        return queryFactory
                .select(Projections.constructor(MonthlyDepositSummaryDto.class,
                        rpd.payYearMonth,
                        rpd.employee.empId.countDistinct().intValue(),
                        rpd.depositAmount.sum()
                ))
                .from(rpd)
                .where(
                        rpd.company.companyId.eq(companyId),
                        rpd.depStatus.eq(DepStatus.COMPLETED),
                        rpd.payYearMonth.startsWith(String.valueOf(year))
                )
                .groupBy(rpd.payYearMonth)
                .orderBy(rpd.payYearMonth.asc())
                .fetch();
    }

    // ── 7. 사원별 집계 (화면 메인 테이블용) ──
    @Override
    public List<PensionDepositByEmployeeResDto> searchByEmployee(
            UUID companyId, String fromYm, String toYm,
            String search, Long deptId, DepStatus status) {

        // COMPLETED 건만 집계 (CASE WHEN으로 조건부 SUM)
        NumberExpression<Integer> monthCountExpr =
                new CaseBuilder()
                        .when(rpd.depStatus.eq(DepStatus.COMPLETED)).then(1)
                        .otherwise(0)
                        .sum();

        NumberExpression<Long> totalAmountExpr =
                new CaseBuilder()
                        .when(rpd.depStatus.eq(DepStatus.COMPLETED))
                        .then(rpd.depositAmount)
                        .otherwise(0L)
                        .sum();

        // 그룹별 Tuple 조회 (hasManual/hasCanceled는 후처리)
        List<Tuple> rows = queryFactory
                .select(
                        rpd.employee.empId,
                        rpd.employee.empName,
                        rpd.employee.dept.deptName,
                        monthCountExpr,
                        totalAmountExpr,
                        rpd.depositDate.max()
                )
                .from(rpd)
                .join(rpd.employee, qEmp)
                .where(buildByEmployeeWhere(companyId, fromYm, toYm, search, deptId, status, null, null))
                .groupBy(rpd.employee.empId, rpd.employee.empName, rpd.employee.dept.deptName)
                .orderBy(rpd.employee.empName.asc())
                .fetch();

        if (rows.isEmpty()) return List.of();

        // hasManual: 수동 등록된 적립이 1건이라도 있는 사원 empId 집합
        Set<Long> manualEmpIds = new HashSet<>(queryFactory
                .select(rpd.employee.empId)
                .from(rpd)
                .where(buildByEmployeeWhere(companyId, fromYm, toYm, search, deptId, status, true, null))
                .distinct()
                .fetch());

        // hasCanceled: CANCELED 적립이 1건이라도 있는 사원 empId 집합
        Set<Long> canceledEmpIds = new HashSet<>(queryFactory
                .select(rpd.employee.empId)
                .from(rpd)
                .where(buildByEmployeeWhere(companyId, fromYm, toYm, search, deptId, status, null, DepStatus.CANCELED))
                .distinct()
                .fetch());

        List<PensionDepositByEmployeeResDto> result = new ArrayList<>(rows.size());
        for (Tuple t : rows) {
            Long empId = t.get(rpd.employee.empId);
            result.add(PensionDepositByEmployeeResDto.builder()
                    .empId(empId)
                    .empName(t.get(rpd.employee.empName))
                    .deptName(t.get(rpd.employee.dept.deptName))
                    .monthCount(t.get(monthCountExpr))
                    .totalAmount(t.get(totalAmountExpr))
                    .lastDepositDate(t.get(rpd.depositDate.max()))
                    .hasManual(manualEmpIds.contains(empId))
                    .hasCanceled(canceledEmpIds.contains(empId))
                    .build());
        }
        return result;
    }

    // ── 사원별 집계 전용 where 빌더 (보조 조건 isManualFlag, extraStatus 지원) ──
    private BooleanBuilder buildByEmployeeWhere(UUID companyId, String fromYm, String toYm,
                                                String search, Long deptId, DepStatus status,
                                                Boolean isManualFlag, DepStatus extraStatus) {
        BooleanBuilder where = new BooleanBuilder();
        where.and(rpd.company.companyId.eq(companyId));
        if (fromYm != null && !fromYm.isBlank()) where.and(rpd.payYearMonth.goe(fromYm));
        if (toYm != null && !toYm.isBlank()) where.and(rpd.payYearMonth.loe(toYm));
        if (search != null && !search.isBlank()) where.and(rpd.employee.empName.containsIgnoreCase(search));
        if (deptId != null) where.and(rpd.employee.dept.deptId.eq(deptId));
        if (status != null) where.and(rpd.depStatus.eq(status));
        if (isManualFlag != null) where.and(rpd.isManual.eq(isManualFlag));
        if (extraStatus != null) where.and(rpd.depStatus.eq(extraStatus));
        return where;
    }

    // ── 공용 where 빌더 ──
    private BooleanBuilder buildWhere(UUID companyId, String fromYm, String toYm,
                                      Long empId, DepStatus status) {
        BooleanBuilder b = new BooleanBuilder();
        b.and(rpd.company.companyId.eq(companyId));
        if (fromYm != null && !fromYm.isBlank()) b.and(rpd.payYearMonth.goe(fromYm));
        if (toYm != null && !toYm.isBlank()) b.and(rpd.payYearMonth.loe(toYm));
        if (empId != null) b.and(rpd.employee.empId.eq(empId));
        if (status != null) b.and(rpd.depStatus.eq(status));
        return b;
    }
}

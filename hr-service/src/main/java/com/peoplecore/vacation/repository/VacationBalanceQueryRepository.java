package com.peoplecore.vacation.repository;

import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.QEmployee;
import com.peoplecore.vacation.dto.EmployeeVacationAggregationQueryDto;
import com.peoplecore.vacation.entity.QVacationBalance;
import com.peoplecore.vacation.entity.QVacationType;
import com.peoplecore.vacation.entity.VacationBalance;
import com.peoplecore.vacation.entity.VacationType;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/* 휴가 잔여 QueryDSL Repository - fetch join + 복잡 조건 전용 */
@Repository
public class VacationBalanceQueryRepository {

    /* 전사 휴가 관리 "법정연차" 컬럼 집계 기준 코드 - 근로기준법상 연차+월차 2종. */
    /* 다른 법정 휴가(출산/가족돌봄/공가 등) 는 "특별휴가" 집계로 분류됨 */
    private static final Set<String> STATUTORY_BASIC_CODES =
            Set.of(VacationType.CODE_ANNUAL, VacationType.CODE_MONTHLY);

    private final JPAQueryFactory queryFactory;

    @Autowired
    public VacationBalanceQueryRepository(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    /*
     * 전사 휴가 관리 - 회사 전체 사원 휴가 집계 (페이지 없음)
     * 용도: 부서 카드의 평균 소진율/낮은 소진자 수 산정. 전체 사원 집계 후 서비스에서 deptId 그룹핑
     * balance 없는 사원도 포함 - LEFT JOIN 으로 0 row 반환
     * 대상: ACTIVE + delete_at IS NULL
     */
    public List<EmployeeVacationAggregationQueryDto> aggregateAllForCompany(UUID companyId, Integer year) {
        return buildAggregationQuery(companyId, year, null).fetch();
    }

    /*
     * 전사 휴가 관리 - 특정 부서 사원 휴가 집계 (페이지네이션)
     * 용도: 부서 선택 후 사원 상세 테이블
     * content + count 2회 쿼리 (QueryDSL Page 관용 구조)
     * 정렬: Pageable.sort 를 일부만 지원 (현재는 empId 오름차순 고정 - 복잡한 Sort 매핑 비용 회피)
     */
    public Page<EmployeeVacationAggregationQueryDto> aggregateByDeptPageable(
            UUID companyId, Integer year, Long deptId, Pageable pageable) {
        QEmployee e = QEmployee.employee;

        // 입사일 오름차순 - 오래된 사원부터. hireDate 동점 시 empId 로 결정적 정렬
        List<EmployeeVacationAggregationQueryDto> content = buildAggregationQuery(companyId, year, deptId)
                .orderBy(e.empHireDate.asc(), e.empId.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // count 는 동일 WHERE 조건으로 DISTINCT 사원 수 (GROUP BY 후 row 수 = 사원 수)
        Long total = queryFactory
                .select(e.empId.countDistinct())
                .from(e)
                .where(
                        e.company.companyId.eq(companyId),
                        e.deleteAt.isNull(),
                        e.empStatus.eq(EmpStatus.ACTIVE),
                        e.dept.deptId.eq(deptId)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    /*
     * 공통 집계 쿼리 빌더 - aggregateAllForCompany / aggregateByDeptPageable 공용
     * 법정/특별 분기: VacationType.typeCode IN STATUTORY_BASIC_CODES 여부로 CASE WHEN
     * available = total - used - pending - expired (잔여 공식)
     * deptIdOrNull null 이면 부서 필터 제외 (전체 사원)
     * COALESCE(sum, 0) 로 balance 없는 사원도 0 으로 반환 (LEFT JOIN 후 NULL 방어)
     */
    private JPAQuery<EmployeeVacationAggregationQueryDto> buildAggregationQuery(
            UUID companyId, Integer year, Long deptIdOrNull) {
        QVacationBalance b = QVacationBalance.vacationBalance;
        QVacationType t = QVacationType.vacationType;
        QEmployee e = QEmployee.employee;

        // 잔여 공식 - balance 없는 row 는 JOIN 결과 NULL 이라 CASE 에서 걸러짐
        NumberExpression<BigDecimal> available = b.totalDays
                .subtract(b.usedDays).subtract(b.pendingDays).subtract(b.expiredDays);
        // 법정(연차+월차) 분기
        NumberExpression<BigDecimal> statutoryAvail = new CaseBuilder()
                .when(t.typeCode.in(STATUTORY_BASIC_CODES)).then(available)
                .otherwise(BigDecimal.ZERO);
        // 특별(위 2종 외) 분기
        NumberExpression<BigDecimal> specialAvail = new CaseBuilder()
                .when(t.typeCode.in(STATUTORY_BASIC_CODES)).then(BigDecimal.ZERO)
                .otherwise(available);

        BooleanExpression deptFilter = (deptIdOrNull != null) ? e.dept.deptId.eq(deptIdOrNull) : null;

        return queryFactory
                .select(Projections.constructor(EmployeeVacationAggregationQueryDto.class,
                        e.empId,
                        e.dept.deptId,
                        coalesceSum(statutoryAvail),
                        coalesceSum(specialAvail),
                        coalesceSum(b.usedDays),
                        coalesceSum(b.totalDays)
                ))
                .from(e)
                .leftJoin(b).on(
                        b.employee.empId.eq(e.empId),
                        b.companyId.eq(companyId),
                        b.balanceYear.eq(year))
                .leftJoin(b.vacationType, t)
                .where(
                        e.company.companyId.eq(companyId),
                        e.deleteAt.isNull(),
                        e.empStatus.eq(EmpStatus.ACTIVE),
                        deptFilter
                )
                .groupBy(e.empId, e.dept.deptId);
    }

    /* COALESCE(sum(expr), 0) 헬퍼 - balance 없는 사원 0 반환 보장 */
    private NumberExpression<BigDecimal> coalesceSum(NumberExpression<BigDecimal> expr) {
        return Expressions.numberTemplate(BigDecimal.class, "COALESCE(sum({0}), 0)", expr);
    }

    /*
     * 사원의 현 시점 유효 Balance + VacationType fetch join
     * 용도: 휴가 사용 신청 모달 드롭다운 (MyVacationType)
     * 유효 기준: expires_at IS NULL (무기한) OR expires_at >= today (만료 전)
     *   - balance_year 필터 안 쓰는 이유: HIRE 정책은 입사기념일 기준 적립이라 달력연도와 엇갈림.
     *     예) 2025-09 입사자의 2026-09 발생 연차는 balance_year=2026, expires_at=2027-08-31.
     *        2027-01~08 시점엔 balance_year=올해(2027) 쿼리로 매칭 안 됨 → expires_at 조건이 정확.
     * 필터: VacationType.isActive=true (비활성 유형 제외, GRANT 쿼리와 일관)
     * N+1 방지: VacationType fetch join
     * 정렬: VacationType.sortOrder 오름차순
     */
    public List<VacationBalance> findActiveByEmpFetchType(UUID companyId, Long empId, LocalDate today) {
        QVacationBalance b = QVacationBalance.vacationBalance;
        QVacationType t = QVacationType.vacationType;

        return queryFactory
                .selectFrom(b)
                .join(b.vacationType, t).fetchJoin()
                .where(
                        b.companyId.eq(companyId),
                        b.employee.empId.eq(empId),
                        b.expiresAt.isNull().or(b.expiresAt.goe(today)),
                        t.isActive.isTrue()
                )
                .orderBy(t.sortOrder.asc())
                .fetch();
    }

    /*
     * 회사 만료 대상 잔여 조회 (스케줄러)
     * 용도: 만료 잡 - expires_at 도달한 잔여 일괄 처리
     * 조건: expires_at <= targetDate
     * N+1 방지: VacationType + Employee 같이 로드
     * 인덱스: idx_vacation_balance_company_expires
     */
    public List<VacationBalance> findExpiringByCompany(UUID companyId, LocalDate targetDate) {
        QVacationBalance b = QVacationBalance.vacationBalance;
        QVacationType t = QVacationType.vacationType;
        QEmployee e = QEmployee.employee;

        return queryFactory
                .selectFrom(b)
                .join(b.vacationType, t).fetchJoin()
                .join(b.employee, e).fetchJoin()
                .where(
                        b.companyId.eq(companyId),
                        b.expiresAt.isNotNull(),
                        b.expiresAt.loe(targetDate)
                )
                .fetch();
    }

    /*
     * 회사 + 유형 + 연도 잔여 사원 다건 조회 (잔여 > 0)
     * 용도: 촉진 통지 잡 - 특정 유형(연차) 사원별 잔여 순회
     * 조건: totalDays - usedDays - pendingDays > 0
     * N+1 방지: Employee fetch join
     */
    public List<VacationBalance> findRemainingByCompanyAndType(UUID companyId, Long typeId, Integer year) {
        QVacationBalance b = QVacationBalance.vacationBalance;
        QEmployee e = QEmployee.employee;

        return queryFactory
                .selectFrom(b)
                .join(b.employee, e).fetchJoin()
                .where(
                        b.companyId.eq(companyId),
                        b.vacationType.typeId.eq(typeId),
                        b.balanceYear.eq(year),
                        b.totalDays.subtract(b.usedDays).subtract(b.pendingDays).gt(0)
                )
                .fetch();
    }

    /*
     * 회사 + 특정 유형 + 만료일 일치 balance 조회 (1차 촉진 통지 대상).
     * 조건: expires_at == targetExpiresAt. 잔여는 체크하지 않음 (1차 통지 전제).
     * N+1 방지: Employee + VacationType 같이 fetch join.
     */
    public List<VacationBalance> findByCompanyAndTypeAndExpiresAt(UUID companyId, Long typeId, LocalDate expiresAt) {
        QVacationBalance b = QVacationBalance.vacationBalance;
        QVacationType t = QVacationType.vacationType;
        QEmployee e = QEmployee.employee;

        return queryFactory
                .selectFrom(b)
                .join(b.vacationType, t).fetchJoin()
                .join(b.employee, e).fetchJoin()
                .where(
                        b.companyId.eq(companyId),
                        b.vacationType.typeId.eq(typeId),
                        b.expiresAt.eq(expiresAt)
                )
                .fetch();
    }

    /*
     * 회사 + 특정 유형 + 만료일 일치 + 잔여 > 0 balance 조회 (2차 촉진 통지 대상).
     * 잔여 = total - used - pending - expired. 0 이하면 제외.
     */
    public List<VacationBalance> findRemainingByCompanyAndTypeAndExpiresAt(UUID companyId, Long typeId, LocalDate expiresAt) {
        QVacationBalance b = QVacationBalance.vacationBalance;
        QVacationType t = QVacationType.vacationType;
        QEmployee e = QEmployee.employee;

        return queryFactory
                .selectFrom(b)
                .join(b.vacationType, t).fetchJoin()
                .join(b.employee, e).fetchJoin()
                .where(
                        b.companyId.eq(companyId),
                        b.vacationType.typeId.eq(typeId),
                        b.expiresAt.eq(expiresAt),
                        b.totalDays.subtract(b.usedDays).subtract(b.pendingDays).subtract(b.expiredDays).gt(0)
                )
                .fetch();
    }

    /*
     * 1차 촉진 catch-up 조회 - expiresAt BETWEEN [from, to]
     * 용도: 스케줄러가 하루 실패해도 다음 날 미통지분 catch-up. UNIQUE 제약으로 중복 발송 방지
     * 인덱스: idx_vacation_balance_company_expires (회사+expires_at 범위 스캔)
     */
    public List<VacationBalance> findByCompanyAndTypeAndExpiresAtBetween(
            UUID companyId, Long typeId, LocalDate from, LocalDate to) {
        QVacationBalance b = QVacationBalance.vacationBalance;
        QVacationType t = QVacationType.vacationType;
        QEmployee e = QEmployee.employee;

        return queryFactory
                .selectFrom(b)
                .join(b.vacationType, t).fetchJoin()
                .join(b.employee, e).fetchJoin()
                .where(
                        b.companyId.eq(companyId),
                        b.vacationType.typeId.eq(typeId),
                        b.expiresAt.between(from, to)
                )
                .fetch();
    }

    /*
     * 2차 촉진 catch-up 조회 - 잔여 > 0 AND expiresAt BETWEEN [from, to]
     * 용도: 1차 통지 후 만료 임박 시점에 잔여 남은 사원만 2차 통지
     */
    public List<VacationBalance> findRemainingByCompanyAndTypeAndExpiresAtBetween(
            UUID companyId, Long typeId, LocalDate from, LocalDate to) {
        QVacationBalance b = QVacationBalance.vacationBalance;
        QVacationType t = QVacationType.vacationType;
        QEmployee e = QEmployee.employee;

        return queryFactory
                .selectFrom(b)
                .join(b.vacationType, t).fetchJoin()
                .join(b.employee, e).fetchJoin()
                .where(
                        b.companyId.eq(companyId),
                        b.vacationType.typeId.eq(typeId),
                        b.expiresAt.between(from, to),
                        b.totalDays.subtract(b.usedDays).subtract(b.pendingDays).subtract(b.expiredDays).gt(0)
                )
                .fetch();
    }
}
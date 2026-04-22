package com.peoplecore.vacation.repository;

import com.peoplecore.employee.domain.EmpGender;
import com.peoplecore.vacation.dto.GrantableTypeQueryDto;
import com.peoplecore.vacation.entity.GenderLimit;
import com.peoplecore.vacation.entity.QVacationBalance;
import com.peoplecore.vacation.entity.QVacationGrantRequest;
import com.peoplecore.vacation.entity.QVacationType;
import com.peoplecore.vacation.entity.RequestStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/* 휴가 부여 신청 QueryDSL Repository - 동적 조건/단일 쿼리 전용 */
@Repository
public class VacationGrantRequestQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Autowired
    public VacationGrantRequestQueryRepository(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    /*
     * 부여 신청 가능한 법정 유형 + 현재 Balance + 같은 연도 PENDING GRANT 합 - 단일 쿼리
     * 조건 (동적):
     *   - 회사 소속 활성 유형 (company + isActive)
     *   - EVENT_BASED typeCode IN :eventBasedCodes (출산/유산/배우자출산/가족돌봄/공가)
     *   - 본인 성별에 맞는 유형 (genderLimit IN :allowedGenderLimits - ALL + 본인 성별 전용)
     * LEFT JOIN Balance - 처음 신청 유형은 balance 없음 → COALESCE(0) 으로 방어
     * 서브쿼리: 같은 유형의 현재 연도 PENDING GRANT 일수 합 (상관 서브쿼리)
     * 정렬: VacationType.sortOrder 오름차순 (드롭다운 표시 순서)
     */
    public List<GrantableTypeQueryDto> findGrantableTypesForEmp(
            UUID companyId, Long empId, Integer year, EmpGender empGender,
            Collection<String> eventBasedCodes,
            LocalDateTime yearStart, LocalDateTime nextYearStart) {

        QVacationType t = QVacationType.vacationType;
        QVacationBalance b = QVacationBalance.vacationBalance;
        QVacationGrantRequest g = QVacationGrantRequest.vacationGrantRequest;

        // 성별 필터 - 본인 성별에 허용된 GenderLimit 만 통과
        BooleanExpression genderCond = genderCondition(empGender, t);

        // available 계산 = total - used - pendingUse - expired
        NumberExpression<BigDecimal> available = b.totalDays
                .subtract(b.usedDays).subtract(b.pendingDays).subtract(b.expiredDays);

        // 상관 서브쿼리 - 해당 유형의 같은 연도 PENDING GRANT 일수 합 (null → 0 COALESCE)
        NumberExpression<BigDecimal> pendingGrantSum = Expressions.numberTemplate(
                BigDecimal.class, "COALESCE(({0}), 0)",
                JPAExpressions
                        .select(g.requestUseDays.sum())
                        .from(g)
                        .where(
                                g.companyId.eq(companyId),
                                g.employee.empId.eq(empId),
                                g.vacationType.typeId.eq(t.typeId),
                                g.requestStatus.eq(RequestStatus.PENDING),
                                g.createdAt.goe(yearStart),
                                g.createdAt.lt(nextYearStart)
                        )
        );

        return queryFactory
                .select(Projections.constructor(GrantableTypeQueryDto.class,
                        t.typeId,
                        t.typeCode,
                        t.typeName,
                        b.totalDays.coalesce(BigDecimal.ZERO),
                        b.usedDays.coalesce(BigDecimal.ZERO),
                        b.pendingDays.coalesce(BigDecimal.ZERO),
                        available.coalesce(BigDecimal.ZERO),
                        pendingGrantSum
                ))
                .from(t)
                .leftJoin(b).on(
                        b.vacationType.typeId.eq(t.typeId),
                        b.companyId.eq(companyId),
                        b.employee.empId.eq(empId),
                        b.balanceYear.eq(year))
                .where(
                        t.companyId.eq(companyId),
                        t.isActive.isTrue(),
                        t.typeCode.in(eventBasedCodes),
                        genderCond
                )
                .orderBy(t.sortOrder.asc())
                .fetch();
    }

    /* 성별별 허용 GenderLimit 매핑 */
    /* MALE → ALL + MALE_ONLY / FEMALE → ALL + FEMALE_ONLY */
    private BooleanExpression genderCondition(EmpGender empGender, QVacationType t) {
        if (empGender == EmpGender.MALE) {
            return t.genderLimit.in(GenderLimit.ALL, GenderLimit.MALE_ONLY);
        } else if (empGender == EmpGender.FEMALE) {
            return t.genderLimit.in(GenderLimit.ALL, GenderLimit.FEMALE_ONLY);
        }
        // EmpGender 는 MALE/FEMALE 만 존재 - 여기 도달 시 데이터 이상
        throw new IllegalStateException("Unknown EmpGender: " + empGender);
    }
}

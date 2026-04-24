package com.peoplecore.vacation.repository;

import com.peoplecore.vacation.entity.LedgerEventType;
import com.peoplecore.vacation.entity.QVacationBalance;
import com.peoplecore.vacation.entity.QVacationLedger;
import com.peoplecore.vacation.entity.QVacationType;
import com.peoplecore.vacation.entity.VacationLedger;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/* 관리자 조정 이력 조회 전용 QueryDSL Repository */
/* MANUAL_GRANT / MANUAL_USED 만 필터. year / typeId 동적 조건, Slice 스크롤 지원 */
@Repository
public class VacationLedgerQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Autowired
    public VacationLedgerQueryRepository(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    /* 관리자 수동 조정 이력 - MANUAL_GRANT / MANUAL_USED 만. createdAt 최신순 */
    /* Balance + Type fetch join 으로 DTO 변환 시 N+1 방지. manager 는 서비스에서 bulk 조회 */
    /* size+1 로 조회 후 hasNext 판정 → totalElements 계산 안 함 (Slice) */
    public Slice<VacationLedger> findManualAdjustments(UUID companyId, Long empId,
                                                       Integer year, Long typeId,
                                                       Pageable pageable) {
        QVacationLedger l = QVacationLedger.vacationLedger;
        QVacationBalance b = QVacationBalance.vacationBalance;
        QVacationType t = QVacationType.vacationType;

        BooleanBuilder where = new BooleanBuilder()
                .and(l.companyId.eq(companyId))
                .and(l.empId.eq(empId))
                .and(l.eventType.in(LedgerEventType.MANUAL_GRANT, LedgerEventType.MANUAL_USED));

        if (year != null) where.and(b.balanceYear.eq(year));
        if (typeId != null) where.and(t.typeId.eq(typeId));

        int pageSize = pageable.getPageSize();
        List<VacationLedger> rows = queryFactory
                .selectFrom(l)
                .join(l.vacationBalance, b).fetchJoin()
                .join(b.vacationType, t).fetchJoin()
                .where(where)
                .orderBy(l.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageSize + 1L)
                .fetch();

        boolean hasNext = rows.size() > pageSize;
        if (hasNext) rows.remove(rows.size() - 1);

        return new SliceImpl<>(rows, pageable, hasNext);
    }
}

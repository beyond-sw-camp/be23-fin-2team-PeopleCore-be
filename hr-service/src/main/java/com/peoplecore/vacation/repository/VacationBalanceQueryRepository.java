package com.peoplecore.vacation.repository;

import com.peoplecore.employee.domain.QEmployee;
import com.peoplecore.vacation.entity.QVacationBalance;
import com.peoplecore.vacation.entity.QVacationType;
import com.peoplecore.vacation.entity.VacationBalance;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/* 휴가 잔여 QueryDSL Repository - fetch join + 복잡 조건 전용 */
@Repository
public class VacationBalanceQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Autowired
    public VacationBalanceQueryRepository(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    /*
     * 사원 특정 연도 모든 유형 잔여 + VacationType fetch join
     * 용도: 사원 "내 잔여 보기" 화면 (월차/연차/특수휴가 등 한 번에)
     * N+1 방지: VacationType 같이 로드 (typeName/typeCode 표시용)
     * 정렬: VacationType.sortOrder 오름차순
     */
    public List<VacationBalance> findAllByEmpYearFetchType(UUID companyId, Long empId, Integer year) {
        QVacationBalance b = QVacationBalance.vacationBalance;
        QVacationType t = QVacationType.vacationType;

        return queryFactory
                .selectFrom(b)
                .join(b.vacationType, t).fetchJoin()
                .where(
                        b.companyId.eq(companyId),
                        b.employee.empId.eq(empId),
                        b.balanceYear.eq(year)
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
}
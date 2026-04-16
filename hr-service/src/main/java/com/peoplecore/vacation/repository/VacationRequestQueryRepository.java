package com.peoplecore.vacation.repository;

import com.peoplecore.attendance.dto.VacationSlice;
import com.peoplecore.employee.domain.QEmployee;
import com.peoplecore.vacation.entity.QVacationRequest;
import com.peoplecore.vacation.entity.QVacationType;
import com.peoplecore.vacation.entity.RequestStatus;
import com.peoplecore.vacation.entity.VacationRequest;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/* 휴가 신청 QueryDSL Repository - 페이지/fetch join/복잡 조건 전용 */
@Repository
public class VacationRequestQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Autowired
    public VacationRequestQueryRepository(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    /*
     * 사원 신청 이력 페이지 조회 + VacationType fetch join
     * 용도: 사원 "내 휴가 신청 내역" 화면
     * N+1 방지: VacationType 같이 로드 (typeName 표시용)
     * 정렬: createdAt 내림차순 (최신순)
     */
    public Page<VacationRequest> findEmployeeHistory(UUID companyId, Long empId, Pageable pageable) {
        QVacationRequest r = QVacationRequest.vacationRequest;
        QVacationType t = QVacationType.vacationType;

        List<VacationRequest> content = queryFactory
                .selectFrom(r)
                .join(r.vacationType, t).fetchJoin()
                .where(
                        r.companyId.eq(companyId),
                        r.employee.empId.eq(empId)
                )
                .orderBy(r.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(r.count())
                .from(r)
                .where(
                        r.companyId.eq(companyId),
                        r.employee.empId.eq(empId)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    /*
     * 회사 상태별 신청 페이지 조회 + VacationType + Employee fetch join
     * 용도: 관리자 화면 "결재 대기/승인/반려/취소" 탭별 목록
     * N+1 방지: Type + Employee 같이 로드
     * 정렬: createdAt 내림차순
     */
    public Page<VacationRequest> findByCompanyAndStatus(UUID companyId, RequestStatus status, Pageable pageable) {
        QVacationRequest r = QVacationRequest.vacationRequest;
        QVacationType t = QVacationType.vacationType;
        QEmployee e = QEmployee.employee;

        List<VacationRequest> content = queryFactory
                .selectFrom(r)
                .join(r.vacationType, t).fetchJoin()
                .join(r.employee, e).fetchJoin()
                .where(
                        r.companyId.eq(companyId),
                        r.requestStatus.eq(status)
                )
                .orderBy(r.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(r.count())
                .from(r)
                .where(
                        r.companyId.eq(companyId),
                        r.requestStatus.eq(status)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    /*
     * 사원 + 기간 교집합 + 승인 휴가 슬라이스 조회
     * 용도: 주간/월간 근태 요약 화면 - 휴가 사용 일자 표시
     * 조건: 승인된 휴가 중 [weekStart, weekEnd] 와 교집합 있는 것
     * Projection: VacationSlice (필요 컬럼만, 근태 모듈 호환)
     */
    public List<VacationSlice> findApprovedSlicesInWeek(UUID companyId, Long empId,
                                                        RequestStatus status,
                                                        LocalDateTime weekStart,
                                                        LocalDateTime weekEnd) {
        QVacationRequest r = QVacationRequest.vacationRequest;

        return queryFactory
                .select(Projections.constructor(VacationSlice.class,
                        r.requestStartAt, r.requestEndAt, r.requestUseDays))
                .from(r)
                .where(
                        r.companyId.eq(companyId),
                        r.employee.empId.eq(empId),
                        r.requestStatus.eq(status),
                        r.requestStartAt.loe(weekEnd),
                        r.requestEndAt.goe(weekStart)
                )
                .fetch();
    }
}
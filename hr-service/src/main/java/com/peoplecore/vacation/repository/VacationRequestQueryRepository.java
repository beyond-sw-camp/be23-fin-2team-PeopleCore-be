package com.peoplecore.vacation.repository;

import com.peoplecore.attendance.dto.VacationSlice;
import com.peoplecore.employee.domain.QEmployee;
import com.peoplecore.vacation.dto.VacationAdminPeriodResponseDto;
import com.peoplecore.vacation.entity.QVacationRequest;
import com.peoplecore.vacation.entity.QVacationType;
import com.peoplecore.vacation.entity.RequestStatus;
import com.peoplecore.vacation.entity.VacationRequest;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
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
     * 전사 휴가 관리 - 기간 교집합 + 상태 복수 필터 페이지 조회
     * 용도: 관리자 전사 휴가 현황 화면 (기간별 PENDING/APPROVED 혼합 조회)
     * 조건: 휴가 기간 [startAt, endAt] 이 요청 [periodStart, periodEnd] 와 교집합
     *       즉 r.startAt <= periodEnd 그리고 r.endAt >= periodStart
     * 상태: statuses 배열에 포함된 것만. 비어있으면 전체
     * N+1 방지: VacationType fetch join (empName/deptName 은 스냅샷 컬럼 사용 → Employee 조인 불필요)
     * 정렬: requestStartAt 오름차순 (일정순 - 달력 보기 편함)
     */
    /*
     * 전사 휴가 관리 기간 조회 - 사원별 요약 (GROUP BY emp_id)
     * 같은 사원이 기간 내 여러 슬롯/결재 있어도 1 entry 로 묶음 ("몇 명이 휴가인가" UX)
     * 정렬: 사원의 MIN(requestStartAt) 오름차순
     * totalElements = 기간 내 휴가자 distinct 수
     */
    public Page<VacationAdminPeriodResponseDto> findByCompanyAndPeriodAndStatuses(UUID companyId,
                                                                                  LocalDateTime periodStart,
                                                                                  LocalDateTime periodEnd,
                                                                                  List<RequestStatus> statuses,
                                                                                  Pageable pageable) {
        QVacationRequest r = QVacationRequest.vacationRequest;

        BooleanExpression statusPredicate = (statuses == null || statuses.isEmpty())
                ? null
                : r.requestStatus.in(statuses);

        // 1단계: 사원별 요약 페이지 - 합계/시점 범위/건수를 GROUP BY 로 한 번에 집계
        List<VacationAdminPeriodResponseDto> content = queryFactory
                .select(Projections.constructor(VacationAdminPeriodResponseDto.class,
                        r.employee.empId,
                        r.requestEmpName.min(),           // 스냅샷 중 하나 (동일 empId 면 동일)
                        r.requestEmpDeptName.min(),       // 스냅샷 중 하나
                        r.requestUseDays.sum(),           // 기간 내 총 사용일수
                        r.requestStartAt.min(),           // 최초 시작
                        r.requestEndAt.max(),             // 최종 종료
                        r.count()))                       // 기간 내 슬롯 건수
                .from(r)
                .where(
                        r.companyId.eq(companyId),
                        r.requestStartAt.loe(periodEnd),
                        r.requestEndAt.goe(periodStart),
                        statusPredicate
                )
                .groupBy(r.employee.empId)
                .orderBy(r.requestStartAt.min().asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 2단계: 총 휴가자 수 (distinct empId) - "몇 명" count 제공
        Long total = queryFactory
                .select(r.employee.empId.countDistinct())
                .from(r)
                .where(
                        r.companyId.eq(companyId),
                        r.requestStartAt.loe(periodEnd),
                        r.requestEndAt.goe(periodStart),
                        statusPredicate
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
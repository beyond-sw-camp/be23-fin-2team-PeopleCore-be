package com.peoplecore.vacation.repository;

import com.peoplecore.attendance.dto.VacationSlice;
import com.peoplecore.employee.domain.QEmployee;
import com.peoplecore.vacation.dto.VacationAdminPeriodPageResponse;
import com.peoplecore.vacation.dto.VacationAdminPeriodResponseDto;
import com.peoplecore.vacation.entity.QVacationRequest;
import com.peoplecore.vacation.entity.QVacationType;
import com.peoplecore.vacation.entity.RequestStatus;
import com.peoplecore.vacation.entity.VacationRequest;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
     * 전사 휴가 관리 - 기간 교집합 + 상태 필터 / 건별 페이지 + 메타
     * 용도: 관리자 전사 휴가 현황 화면 (건별 리스트 + "몇 명" / "총 며칠" 요약)
     * 조건: 휴가 기간 [startAt, endAt] 이 요청 [periodStart, periodEnd] 와 교집합
     *       즉 r.startAt <= periodEnd 그리고 r.endAt >= periodStart
     * 상태: statuses 배열에 포함된 것만 (서비스에서 미지정 시 APPROVED 강제)
     * 페이지: content = 건별 row (GROUP BY 없음, 사원 중복 허용)
     * 메타: uniqueEmployeeCount = distinct empId / totalUseDays = sum(useDays)
     * N+1 방지: VacationType join 으로 typeName 프로젝션 (DTO Projection 이라 fetch join 아님)
     * 정렬: requestStartAt ASC (일정순 - 달력/타임라인 친화)
     * 성능: 메타 3개(총 건수/휴가자 수/총 일수) 한 쿼리 Tuple 로 집계 → 총 2 쿼리
     */
    public VacationAdminPeriodPageResponse findByCompanyAndPeriodAndStatuses(UUID companyId,
                                                                              LocalDateTime periodStart,
                                                                              LocalDateTime periodEnd,
                                                                              List<RequestStatus> statuses,
                                                                              Pageable pageable) {
        QVacationRequest r = QVacationRequest.vacationRequest;
        QVacationType t = QVacationType.vacationType;

        // 상태 필터 - statuses 가 비어있으면 null (서비스에서 기본값 주입하므로 실제로는 비어오지 않음)
        BooleanExpression statusPredicate = (statuses == null || statuses.isEmpty())
                ? null
                : r.requestStatus.in(statuses);

        // 1단계: 건별 페이지 - 각 신청 1 row. typeName 은 VacationType join 으로 가져옴
        List<VacationAdminPeriodResponseDto> content = queryFactory
                .select(Projections.constructor(VacationAdminPeriodResponseDto.class,
                        r.requestId,
                        r.employee.empId,
                        r.requestEmpName,
                        r.requestEmpDeptName,
                        t.typeName,
                        r.requestStartAt,
                        r.requestEndAt,
                        r.requestUseDays))
                .from(r)
                .join(r.vacationType, t)
                .where(
                        r.companyId.eq(companyId),
                        r.requestStartAt.loe(periodEnd),
                        r.requestEndAt.goe(periodStart),
                        statusPredicate
                )
                .orderBy(r.requestStartAt.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 2단계: 메타 3개(총 건수/휴가자 수/총 일수) 한 번에 집계
        Tuple meta = queryFactory
                .select(r.count(),
                        r.employee.empId.countDistinct(),
                        r.requestUseDays.sum())
                .from(r)
                .where(
                        r.companyId.eq(companyId),
                        r.requestStartAt.loe(periodEnd),
                        r.requestEndAt.goe(periodStart),
                        statusPredicate
                )
                .fetchOne();

        long totalRows = (meta != null && meta.get(r.count()) != null) ? meta.get(r.count()) : 0L;
        Long uniqueEmpCount = (meta != null) ? meta.get(r.employee.empId.countDistinct()) : 0L;
        BigDecimal totalUseDays = (meta != null && meta.get(r.requestUseDays.sum()) != null)
                ? meta.get(r.requestUseDays.sum())
                : BigDecimal.ZERO;

        Page<VacationAdminPeriodResponseDto> page = new PageImpl<>(content, pageable, totalRows);

        return VacationAdminPeriodPageResponse.builder()
                .page(page)
                .uniqueEmployeeCount(uniqueEmpCount != null ? uniqueEmpCount : 0L)
                .totalUseDays(totalUseDays)
                .build();
    }

    /*
     * 사원 + 연도 구간 교집합 신청 전체 + VacationType fetch join
     * 용도: 내 휴가 현황 페이지 (예정/지난 분류용 원천)
     * 조건: 휴가기간 [startAt,endAt] 이 [year-01-01 00:00, year-12-31 23:59:59] 와 교집합
     * N+1 방지: VacationType fetch join
     * 정렬: requestStartAt ASC (지난 리스트는 서비스에서 재정렬)
     */
    public List<VacationRequest> findByEmpAndYearOverlapFetchType(UUID companyId, Long empId,
                                                                  LocalDateTime yearStart,
                                                                  LocalDateTime yearEnd) {
        QVacationRequest r = QVacationRequest.vacationRequest;
        QVacationType t = QVacationType.vacationType;

        return queryFactory
                .selectFrom(r)
                .join(r.vacationType, t).fetchJoin()
                .where(
                        r.companyId.eq(companyId),
                        r.employee.empId.eq(empId),
                        r.requestStartAt.loe(yearEnd),
                        r.requestEndAt.goe(yearStart)
                )
                .orderBy(r.requestStartAt.asc())
                .fetch();
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
        QVacationType t = QVacationType.vacationType;

        return queryFactory
                .select(Projections.constructor(VacationSlice.class,
                        r.requestStartAt, r.requestEndAt, r.requestUseDays, t.typeName))
                .from(r)
                .join(r.vacationType, t)
                .where(
                        r.companyId.eq(companyId),
                        r.employee.empId.eq(empId),
                        r.requestStatus.eq(status),
                        r.requestStartAt.loe(weekEnd),
                        r.requestEndAt.goe(weekStart)
                )
                .fetch();
    }

    /*
     * 사원 "예정 휴가" 페이지 조회 - 휴가현황 페이지 upcoming 탭
     * 조건: year 기간과 겹치는 신청 중
     *   - PENDING (대기 전부)
     *   - APPROVED 이고 requestEndAt >= now (진행중 포함)
     * 정렬: requestStartAt 오름차순 (가까운 일정 먼저)
     * N+1 방지: VacationType fetch join (typeName 표시용)
     * fetch join + paging 충돌 방지: count 쿼리 분리
     */
    public Page<VacationRequest> findUpcomingPage(UUID companyId, Long empId,
                                                  LocalDateTime yearStart, LocalDateTime yearEnd,
                                                  LocalDateTime now, Pageable pageable) {
        QVacationRequest r = QVacationRequest.vacationRequest;
        QVacationType t = QVacationType.vacationType;

        BooleanExpression upcomingCond = r.requestStatus.eq(RequestStatus.PENDING)
                .or(r.requestStatus.eq(RequestStatus.APPROVED).and(r.requestEndAt.goe(now)));

        List<VacationRequest> content = queryFactory
                .selectFrom(r)
                .join(r.vacationType, t).fetchJoin()
                .where(
                        r.companyId.eq(companyId),
                        r.employee.empId.eq(empId),
                        r.requestStartAt.loe(yearEnd),
                        r.requestEndAt.goe(yearStart),
                        upcomingCond
                )
                .orderBy(r.requestStartAt.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(r.count())
                .from(r)
                .where(
                        r.companyId.eq(companyId),
                        r.employee.empId.eq(empId),
                        r.requestStartAt.loe(yearEnd),
                        r.requestEndAt.goe(yearStart),
                        upcomingCond
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    /*
     * 사원 "지난 휴가" 페이지 조회 - 휴가현황 페이지 past 탭
     * 조건: year 기간과 겹치는 신청 중
     *   - REJECTED / CANCELED (종결)
     *   - APPROVED 이고 requestEndAt < now (종료됨)
     * 정렬: requestEndAt 내림차순 (최근 종료 먼저)
     */
    public Page<VacationRequest> findPastPage(UUID companyId, Long empId,
                                              LocalDateTime yearStart, LocalDateTime yearEnd,
                                              LocalDateTime now, Pageable pageable) {
        QVacationRequest r = QVacationRequest.vacationRequest;
        QVacationType t = QVacationType.vacationType;

        BooleanExpression pastCond = r.requestStatus.in(RequestStatus.REJECTED, RequestStatus.CANCELED)
                .or(r.requestStatus.eq(RequestStatus.APPROVED).and(r.requestEndAt.lt(now)));

        List<VacationRequest> content = queryFactory
                .selectFrom(r)
                .join(r.vacationType, t).fetchJoin()
                .where(
                        r.companyId.eq(companyId),
                        r.employee.empId.eq(empId),
                        r.requestStartAt.loe(yearEnd),
                        r.requestEndAt.goe(yearStart),
                        pastCond
                )
                .orderBy(r.requestEndAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(r.count())
                .from(r)
                .where(
                        r.companyId.eq(companyId),
                        r.employee.empId.eq(empId),
                        r.requestStartAt.loe(yearEnd),
                        r.requestEndAt.goe(yearStart),
                        pastCond
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    /*
     * 특정 근무그룹 + 특정 날짜에 승인 휴가가 걸린 사원 empId Set 조회
     * 용도: 결근 배치 — 휴가자 결근 처리 제외
     * 조건: APPROVED + (startAt <= dayEnd && endAt >= dayStart) — 반차/시간휴가 포함
     * workGroup 필터로 다른 그룹 사원 컷 (성능)
     */
    public Set<Long> findOnLeaveEmpIds(UUID companyId, Long workGroupId, LocalDate targetDate) {
        QVacationRequest r = QVacationRequest.vacationRequest;
        QEmployee e = QEmployee.employee;

        LocalDateTime dayStart = targetDate.atStartOfDay();
        LocalDateTime dayEnd = targetDate.atTime(LocalTime.MAX);

        return new HashSet<>(queryFactory
                .select(r.employee.empId)
                .from(r)
                .join(r.employee, e)
                .where(
                        r.companyId.eq(companyId),
                        r.requestStatus.eq(RequestStatus.APPROVED),
                        e.workGroup.workGroupId.eq(workGroupId),
                        r.requestStartAt.loe(dayEnd),
                        r.requestEndAt.goe(dayStart)
                )
                .distinct()
                .fetch());
    }

    /* 내 캘린더용 휴가 - PENDING+APPROVED 기간 교집합, VacationType JOIN FETCH */
    public List<VacationRequest> findMyCalendarVacations(UUID companyId, Long empId,
                                                          LocalDateTime periodStart,
                                                          LocalDateTime periodEnd) {
        QVacationRequest r = QVacationRequest.vacationRequest;
        QVacationType t = QVacationType.vacationType;

        return queryFactory
                .selectFrom(r)
                .join(r.vacationType, t).fetchJoin()
                .where(
                        r.companyId.eq(companyId),
                        r.employee.empId.eq(empId),
                        r.requestStatus.in(RequestStatus.PENDING, RequestStatus.APPROVED),
                        r.requestStartAt.loe(periodEnd),
                        r.requestEndAt.goe(periodStart)
                )
                .orderBy(r.requestStartAt.asc())
                .fetch();
    }
}
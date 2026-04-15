package com.peoplecore.attendance.repository;

import com.peoplecore.attendance.entity.OtStatus;
import com.peoplecore.attendance.entity.OvertimeRequest;
import com.peoplecore.attendance.entity.QOvertimeRequest;
import com.peoplecore.department.domain.QDepartment;
import com.peoplecore.employee.domain.QEmployee;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/* 관리자 — 초과근무 신청 목록 조회 (QueryDSL).
 * - Employee + Department fetch join 으로 N+1 차단 (행마다 부서명/이름 LAZY 호출 방지)
 * - 정렬: otDate DESC, otId DESC (최신 신청부터)
 * - 인덱스: idx_ot_req_company_status (company_id, ot_status) 활용 — 상태 탭일수록 효과 큼 */
@Repository
public class OvertimeRequestAdminQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Autowired
    public OvertimeRequestAdminQueryRepository(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    /* status null → 전체 탭, 그 외 → 해당 상태만 */
    public List<OvertimeRequest> findPage(UUID companyId, OtStatus status, int page, int size) {
        QOvertimeRequest o = QOvertimeRequest.overtimeRequest;
        QEmployee e = QEmployee.employee;
        QDepartment d = QDepartment.department;

        return queryFactory
                .selectFrom(o)
                .join(o.employee, e).fetchJoin()        // 신청자 (empName) — N+1 차단
                .join(e.dept, d).fetchJoin()            // 부서 (deptName) — N+1 차단
                .where(buildWhere(o, companyId, status))
                .orderBy(o.otDate.desc(), o.otId.desc())
                .offset((long) page * size)
                .limit(size)
                .fetch();
    }

    /* 페이징 totalElements 용 카운트 — fetch join 없이 (count 쿼리 최적화) */
    public long countAll(UUID companyId, OtStatus status) {
        QOvertimeRequest o = QOvertimeRequest.overtimeRequest;
        Long count = queryFactory
                .select(o.count())
                .from(o)
                .where(buildWhere(o, companyId, status))
                .fetchOne();
        return count != null ? count : 0L;
    }

    private BooleanBuilder buildWhere(QOvertimeRequest o, UUID companyId, OtStatus status) {
        BooleanBuilder b = new BooleanBuilder();
        b.and(o.companyId.eq(companyId));               // 멀티테넌시 격리
        if (status != null) b.and(o.otStatus.eq(status)); // null → 전체 탭
        return b;
    }
}

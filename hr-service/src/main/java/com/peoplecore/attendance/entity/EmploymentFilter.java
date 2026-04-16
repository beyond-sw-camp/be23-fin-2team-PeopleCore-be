package com.peoplecore.attendance.entity;

import com.peoplecore.employee.domain.EmpStatus;

import java.util.EnumSet;
import java.util.Set;

/**
 * 근태 현황 대시보드 재직상태 필터.
 * RESIGNED 는 어떤 필터에서도 제외되므로 토큰에 넣지 않는다.
 */
public enum EmploymentFilter {

    /** 전체 — 재직(ACTIVE) + 휴직(ON_LEAVE) */
    ALL(EnumSet.of(EmpStatus.ACTIVE, EmpStatus.ON_LEAVE)),

    /** 재직 — ACTIVE 만 */
    ACTIVE(EnumSet.of(EmpStatus.ACTIVE)),

    /** 휴직 — ON_LEAVE 만 */
    ON_LEAVE(EnumSet.of(EmpStatus.ON_LEAVE));

    private final Set<EmpStatus> allowedStatuses;

    EmploymentFilter(Set<EmpStatus> allowedStatuses) {
        this.allowedStatuses = allowedStatuses;
    }

    /** 이 필터가 포함하는 EmpStatus 집합 — QueryDSL where 절 IN 조건에 사용 */
    public Set<EmpStatus> getAllowedStatuses() {
        return allowedStatuses;
    }
}

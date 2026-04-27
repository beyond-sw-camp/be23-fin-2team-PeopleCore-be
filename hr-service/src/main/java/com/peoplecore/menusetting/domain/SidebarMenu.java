package com.peoplecore.menusetting.domain;

import com.peoplecore.employee.domain.EmpRole;

import java.util.EnumSet;
import java.util.Set;

/**
 * 사이드바에 노출되는 메뉴 enum
 * - alwaysOn: true 면 토글 불가 + 항상 표시 (DASHBOARD 만 해당)
 * - requiredRoles: 비어있으면 전체 공개, 값이 있으면 해당 역할만 메뉴에 접근 가능
 * - defaultOrder: 신규 사원 기본 정렬 순서 (1부터 시작)
 *
 * 접근 가능 여부(isAccessibleBy) 와 토글 가능 여부(alwaysOn) 는 독립적으로 판단.
 */
public enum SidebarMenu {
    /* 대시보드 - 항상 ON, 토글 불가 */
    DASHBOARD(true, EnumSet.noneOf(EmpRole.class), 1),
    /* 전자결재 */
    APPROVAL(false, EnumSet.noneOf(EmpRole.class), 2),
    /* 캘린더 */
    CALENDAR(false, EnumSet.noneOf(EmpRole.class), 3),
    /* 파일함 */
    FILES(false, EnumSet.noneOf(EmpRole.class), 4),
    /* 근태 / 연차 */
    ATTENDANCE(false, EnumSet.noneOf(EmpRole.class), 5),
    /* 급여 */
    PAYROLL(false, EnumSet.noneOf(EmpRole.class), 6),
    /* 성과 관리 */
    PERFORMANCE(false, EnumSet.noneOf(EmpRole.class), 7),
    /* 사원 관리 - HR 관리자/최고관리자만 접근 */
    EMPLOYEE_MGMT(false, EnumSet.of(EmpRole.HR_ADMIN, EmpRole.HR_SUPER_ADMIN), 8),
    /* 급여 관리 - HR 관리자/최고관리자만 접근 */
    PAYROLL_MGMT(false, EnumSet.of(EmpRole.HR_ADMIN, EmpRole.HR_SUPER_ADMIN), 9),
    /* 인사통합 - HR 관리자/최고관리자만 접근 */
    HR_INTEGRATION(false, EnumSet.of(EmpRole.HR_ADMIN, EmpRole.HR_SUPER_ADMIN), 10),
    /* 평가 관리 - HR 관리자/최고관리자만 접근 */
    EVAL_ADMIN(false, EnumSet.of(EmpRole.HR_ADMIN, EmpRole.HR_SUPER_ADMIN), 11),
    /* 근태/휴가 관리 - HR 관리자/최고관리자만 접근 */
    ATTENDANCE_ADMIN(false, EnumSet.of(EmpRole.HR_ADMIN, EmpRole.HR_SUPER_ADMIN), 12);

    private final boolean alwaysOn;
    private final Set<EmpRole> requiredRoles;
    private final int defaultOrder;

    SidebarMenu(boolean alwaysOn, Set<EmpRole> requiredRoles, int defaultOrder) {
        this.alwaysOn = alwaysOn;
        this.requiredRoles = requiredRoles;
        this.defaultOrder = defaultOrder;
    }

    public boolean isAlwaysOn() { return alwaysOn; }
    public Set<EmpRole> getRequiredRoles() { return requiredRoles; }
    public int getDefaultOrder() { return defaultOrder; }

    /** 해당 역할이 이 메뉴를 볼 수 있는지 (requiredRoles 가 비어있으면 항상 true) */
    public boolean isAccessibleBy(EmpRole role) {
        return requiredRoles.isEmpty() || requiredRoles.contains(role);
    }
}

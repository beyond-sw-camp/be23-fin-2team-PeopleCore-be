package com.peoplecore.permission.repository;

import com.peoplecore.employee.domain.EmpRole;
import com.peoplecore.employee.domain.QEmployee;
import com.peoplecore.permission.dto.AdminUserResDto;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class PermissionRepositoryCustomImpl implements PermissionRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QEmployee emp = QEmployee.employee;

    public PermissionRepositoryCustomImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public Page<AdminUserResDto> findAdminList(UUID companyId, String keyword, Long deptId, EmpRole empRole, String sortField, Pageable pageable) {

        // 데이터 조회
        List<AdminUserResDto> content = queryFactory
                .select(Projections.constructor(AdminUserResDto.class,
                        emp.empId,
                        emp.empName,
                        emp.empNum,
                        emp.dept.deptName,
                        emp.grade.gradeName,
                        emp.empRole,
                        emp.empEmail
                ))
                .from(emp)
                .leftJoin(emp.dept)
                .leftJoin(emp.grade)
                .where(
                        companyEq(companyId),
                        adminRoleOnly(),
                        deptEq(deptId),
                        roleEq(empRole),
                        keywordContains(keyword),
                        emp.deleteAt.isNull()
                )
                .orderBy(getOrderSpecifier(sortField))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 전체 건수 조회
        Long total = queryFactory
                .select(emp.count())
                .from(emp)
                .where(
                        companyEq(companyId),
                        adminRoleOnly(),
                        deptEq(deptId),
                        roleEq(empRole),
                        keywordContains(keyword),
                        emp.deleteAt.isNull()
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    // 회사 필터
    private BooleanExpression companyEq(UUID companyId) {
        return companyId != null ? emp.company.companyId.eq(companyId) : null;
    }

    // 관리자 권한만 조회
    private BooleanExpression adminRoleOnly() {
        return emp.empRole.in(EmpRole.HR_ADMIN, EmpRole.HR_SUPER_ADMIN);
    }

    // 부서 필터
    private BooleanExpression deptEq(Long deptId) {
        return deptId != null ? emp.dept.deptId.eq(deptId) : null;
    }

    // 권한 필터
    private BooleanExpression roleEq(EmpRole empRole) {
        return empRole != null ? emp.empRole.eq(empRole) : null;
    }

    // 키워드 검색 (이름 또는 사번)
    private BooleanExpression keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        return emp.empName.containsIgnoreCase(keyword)
                .or(emp.empNum.containsIgnoreCase(keyword));
    }

    // 정렬 조건 매핑
    private OrderSpecifier<?> getOrderSpecifier(String sortField) {
        if (sortField == null) return emp.empName.asc();
        return switch (sortField) {
            case "empNum" -> emp.empNum.asc();
            case "role" -> emp.empRole.asc();
            case "dept" -> emp.dept.deptName.asc();
            default -> emp.empName.asc();
        };
    }
}
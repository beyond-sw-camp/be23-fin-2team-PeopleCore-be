package com.peoplecore.employee.repository;

import com.peoplecore.department.domain.QDepartment;
import com.peoplecore.employee.domain.*;
import com.peoplecore.employee.dto.EmpDetailResponseDto;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

//**********************************************************************************
@Repository
@RequiredArgsConstructor
public class EmployeeRepositoryImpl implements EmployeeRepositoryCustom {
    // QueryDSL 쿼리 실행 객체
    private final JPAQueryFactory queryFactory;
    // Employee 엔티티를 쿼리에서 사용하기 위한 Q클래스 (변수명 충돌 방지를 위해 q 접두사)
    private final QEmployee qEmployee = QEmployee.employee;


    @Override
    public Page<Employee> findAllwithFilter(String keyword, Long deptId, EmpType empType, EmpStatus empStatus, EmployeeSortField sortField, Pageable pageable) {
        // 실제 데이터 조회 (fetch join으로 N+1 방지)
        List<Employee> content = queryFactory
                .selectFrom(qEmployee)                      // Employee 테이블 조회
                .join(qEmployee.dept).fetchJoin()               // 부서 한번에 조회
                .join(qEmployee.grade).fetchJoin()          // 직급 한번에 조회
                .leftJoin(qEmployee.title).fetchJoin()      // 직책 한번에 조회 (nullable)
                .where(
                        keywordContains(keyword),           // 이름 또는 사번 검색 (null이면 조건 무시)
                        deptEq(deptId),                     // 부서 필터 (null이면 조건 무시)
                        empTypeEq(empType),                 // 고용형태 필터 (null이면 조건 무시)
                        empStatusEq(empStatus)              // 재직상태 필터 (null이면 조건 무시)
                )
                .orderBy(getOrderSpecifier(sortField))      // Enum으로 검증된 값만 정렬에 사용 (SQL 인젝션 방지)
                .offset(pageable.getOffset())               // 시작 위치
                .limit(pageable.getPageSize())              // 한 페이지 개수
                .fetch();

//        전체 개수 조회(페이징 처리를 위해 count쿼리 분리)
        Long total = queryFactory
                .select(qEmployee.count())
                .from(qEmployee)
                .where(
                        keywordContains(keyword),
                        deptEq(deptId),
                        empTypeEq(empType),                 // 고용형태 필터 (null이면 조건 무시)
                        empStatusEq(empStatus)
                        )
                .fetchOne();
//                        데이터, 페이지정보, 전체개수 합쳐서 반환
        return new PageImpl<>(content,pageable,total != null ? total : 0L);
    }



//    Enum으로 허용된 값만 정렬에 사용(SQL인젝션 방지)
    private OrderSpecifier<?> getOrderSpecifier(EmployeeSortField sortField) {
        if (sortField == null) return qEmployee.empId.asc(); // 기본 정렬
        return switch (sortField) {
            case EMP_NUM -> qEmployee.empNum.asc(); //사번 오름차순
            case EMP_NAME -> qEmployee.empName.asc(); //이름 오름차순
        };
    }
//    이름 또는 사번에 keyword포함 여부(null이면 where절 자동 무시)
    private BooleanExpression keywordContains(String keyword) {
        return keyword != null ? qEmployee.empName.contains(keyword).or(qEmployee.empNum.contains(keyword)) : null;
    }

//    부서 id일치 여부
private BooleanExpression deptEq(Long deptId) {
    return deptId != null ? qEmployee.dept.deptId.eq(deptId) : null;
}

    //    고용형태 일치 여부
    private BooleanExpression empTypeEq(EmpType empType) {
        return empType != null ? qEmployee.empType.eq(empType) : null;
    }

    //    고용형태 일치 여부
    private BooleanExpression empStatusEq(EmpStatus empStatus) {
        return empStatus != null ? qEmployee.empStatus.eq(empStatus) : null;
    }



}
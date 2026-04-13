package com.peoplecore.attendance.repository;

import com.peoplecore.attendance.dto.WorkGroupResDto;
import com.peoplecore.attendance.entity.QWorkGroup;
import com.peoplecore.employee.domain.QEmployee;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.spel.ast.Projection;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class WorkGroupSearchRepository {

    private final JPAQueryFactory queryFactory;

    @Autowired
    public WorkGroupSearchRepository(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    /*회사별 근무 그룹 목록 + 각 그룹의 멤버 수 조회
     */
    public List<WorkGroupResDto> findWorkGroupWithEmpCount(UUID companyId) {

        QWorkGroup wg = QWorkGroup.workGroup;
        QEmployee e = QEmployee.employee;

        return queryFactory.select(Projections.constructor(WorkGroupResDto.class,
                        wg.workGroupId,
                        wg.groupName,
                        wg.groupCode,
                        wg.groupStartTime,
                        wg.groupEndTime,
                        wg.groupWorkDay,
                        wg.groupMobileCheck,
                        e.empId.count())).from(wg)
                .leftJoin(e).on(e.workGroup.eq(wg))
                .where(wg.company.companyId.eq(companyId)
                        .and(wg.groupDeleteAt
                                .isNull())).groupBy(wg.workGroupId).fetch();
    }


}

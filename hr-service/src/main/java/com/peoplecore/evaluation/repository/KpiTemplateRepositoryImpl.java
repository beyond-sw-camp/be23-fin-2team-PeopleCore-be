package com.peoplecore.evaluation.repository;

import com.peoplecore.department.domain.QDepartment;
import com.peoplecore.evaluation.domain.KpiTemplate;
import com.peoplecore.evaluation.domain.QKpiOption;
import com.peoplecore.evaluation.domain.QKpiTemplate;
import com.peoplecore.evaluation.dto.KpiTemplateResponse;
import com.peoplecore.grade.domain.QGrade;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// KPI지표 QueryDSL 구현체
@Repository
public class KpiTemplateRepositoryImpl implements KpiTemplateRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QKpiTemplate qTemplate = QKpiTemplate.kpiTemplate;
    private final QDepartment qDept = QDepartment.department;
    private final QGrade qGrade = QGrade.grade;
    private final QKpiOption qCategory = new QKpiOption("category");
    private final QKpiOption qUnit = new QKpiOption("unit");

    public KpiTemplateRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    //DTO 직접 변환해서 Page<DTO> 반환
    @Override
    public Page<KpiTemplateResponse> searchTemplates(UUID companyId,
                                                     Long deptId,
                                                     Long gradeId,
                                                     String categoryLabel,
                                                     String keyword,
                                                     Pageable pageable) {

        //    데이터 조회 - Department, 직급, 카테고리, 단위 라벨까지 한 번에 fetchJoin
        //    grade 는 nullable 이라 leftJoin (없는 row 도 노출)
        List<KpiTemplate> content = queryFactory
                .selectFrom(qTemplate)
                .leftJoin(qTemplate.department, qDept).fetchJoin()      // 부서 join
                .leftJoin(qTemplate.grade, qGrade).fetchJoin()          // 직급 join (nullable)
                .leftJoin(qTemplate.category, qCategory).fetchJoin()    // 카테고리 join
                .leftJoin(qTemplate.unit, qUnit).fetchJoin()            // 단위 join
                .where(
                        companyEq(companyId),           // 회사 필터 (Department 기준)
                        qTemplate.isActive.isTrue(),    // 활성 KPI 만 노출
                        deptEq(deptId),                 // 부서 필터
                        gradeEq(gradeId),               // 직급 필터 (해당 직급 OR 전 직급 공통)
                        categoryLabelEq(categoryLabel), // 카테고리 라벨 일치
                        keywordContains(keyword)        // 지표명/설명 검색
                )
                .offset(pageable.getOffset())           // 시작 위치
                .limit(pageable.getPageSize())          // 한 페이지 개수
                .fetch();   // 실행

//        전체건수 (페이징 계산용 - fetchJoin X)
        Long total = queryFactory
                .select(qTemplate.count())
                .from(qTemplate)
                .leftJoin(qTemplate.department, qDept)   // companyEq 필터에 dept 필요
                .leftJoin(qTemplate.category, qCategory) // categoryLabelEq 필터에 category 필요
                .where(
                        companyEq(companyId),
                        qTemplate.isActive.isTrue(),
                        deptEq(deptId),
                        gradeEq(gradeId),
                        categoryLabelEq(categoryLabel),
                        keywordContains(keyword)
                )
                .fetchOne();

//        Entity -> DTO 변환 (fetchJoin 으로 라벨이 메모리에 이미 있음)
        List<KpiTemplateResponse> dtos = new ArrayList<>();
        for (KpiTemplate t : content) {
            dtos.add(KpiTemplateResponse.from(t));
        }

//        데이터 + 페이지정보 + 전체건수 Page 반환
        return new PageImpl<>(dtos, pageable, total != null ? total : 0L);
    }



    //    회사 인증
    private BooleanExpression companyEq(UUID companyId) {
        if (companyId == null) return null;
        return qDept.company.companyId.eq(companyId);
    }

    //    부서 필터 - Department PK 로 일치
    private BooleanExpression deptEq(Long deptId) {
        if (deptId == null) return null;
        return qTemplate.department.deptId.eq(deptId);
    }

    //    직급 필터 - 선택 시 (해당 직급 OR 전 직급 공통) 모두 매칭
    private BooleanExpression gradeEq(Long gradeId) {
        if (gradeId == null) return null;
        return qTemplate.grade.isNull().or(qTemplate.grade.gradeId.eq(gradeId));
    }

    //    카테고리 라벨 일치 (KpiOption.optionValue)
    private BooleanExpression categoryLabelEq(String label) {
        if (label == null || label.isBlank()) return null;
        return qCategory.optionValue.eq(label);
    }

    //    지표명 / 설명 부분 검색 (대소문자 무시)
    private BooleanExpression keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        return qTemplate.name.containsIgnoreCase(keyword).or(qTemplate.description.containsIgnoreCase(keyword));
    }
}

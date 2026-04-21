package com.peoplecore.evaluation.repository;

import com.peoplecore.department.domain.QDepartment;
import com.peoplecore.evaluation.domain.KpiTemplate;
import com.peoplecore.evaluation.domain.QKpiOption;
import com.peoplecore.evaluation.domain.QKpiTemplate;
import com.peoplecore.evaluation.dto.KpiTemplateResponse;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// KPI지표 QueryDSL 구현체
@Repository
public class KpiTemplateRepositoryImpl implements KpiTemplateRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QKpiTemplate qTemplate = QKpiTemplate.kpiTemplate;
    private final QDepartment qDept = QDepartment.department;
    private final QKpiOption qCategory = new QKpiOption("category");
    private final QKpiOption qUnit = new QKpiOption("unit");

    public KpiTemplateRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    //DTO 직접 변환해서 Page<DTO> 반환
    @Override
    public Page<KpiTemplateResponse> searchTemplates(UUID companyId,
                                                     Long deptId,
                                                     String categoryLabel,
                                                     String keyword,
                                                     Set<Long> validDeptIds,    // ★ 추가
                                                     Pageable pageable) {

        //    데이터 조회 - Department, 카테고리, 단위 라벨까지 한 번에 fetchJoin
        List<KpiTemplate> content = queryFactory
                .selectFrom(qTemplate)
                .leftJoin(qTemplate.department, qDept).fetchJoin()      // 부서 join
                .leftJoin(qTemplate.category, qCategory).fetchJoin()    // 카테고리 join
                .leftJoin(qTemplate.unit, qUnit).fetchJoin()            // 단위 join
                .where(
                        companyEq(companyId),           // 회사 필터 (Department 기준)
                        qTemplate.isActive.isTrue(),    // 활성 KPI 만 노출
                        deptInValidIds(validDeptIds),   // depth 정책 IN 필터
                        deptEq(deptId),                 // 부서 필터
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
                        deptInValidIds(validDeptIds),
                        deptEq(deptId),
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

    //    부서 필터 - Department PK 로 일치 (프론트가 옵션 depth 로 필터)
    private BooleanExpression deptEq(Long deptId) {
        if (deptId == null) return null;
        return qTemplate.department.deptId.eq(deptId);
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

    //    ★ 추가: depth 정책에 맞는 부서 IN 필터 (옵션관리 기준)
    //    - null  : 미적용 (정책 무시)
    //    - empty : 결과 0건 강제 (안전 가드)
    private BooleanExpression deptInValidIds(Set<Long> validDeptIds) {
        if (validDeptIds == null) return null;
        if (validDeptIds.isEmpty()) return qTemplate.department.deptId.in(-1L);
        return qTemplate.department.deptId.in(validDeptIds);
    }
}

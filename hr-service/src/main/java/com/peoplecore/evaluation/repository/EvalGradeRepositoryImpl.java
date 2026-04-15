package com.peoplecore.evaluation.repository;

import com.peoplecore.employee.domain.QEmployee;
import com.peoplecore.evaluation.domain.EvalGrade;
import com.peoplecore.evaluation.domain.EvalGradeSortField;
import com.peoplecore.evaluation.domain.QEvalGrade;
import com.peoplecore.evaluation.dto.DraftListItemDto;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 등급 QueryDSL 구현체
@Repository
public class EvalGradeRepositoryImpl implements EvalGradeRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QEvalGrade qGrade = QEvalGrade.evalGrade;
    private final QEmployee qEmployee = QEmployee.employee;

    public EvalGradeRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }


    // 1. 자동 산정 대상 목록
    //  - 시즌/회사 범위 + 부서·키워드 필터 + 정렬/페이징
    @Override
    public Page<DraftListItemDto> searchDraftList(UUID companyId, Long seasonId,
                                                  Long deptId, String keyword,
                                                  EvalGradeSortField sortField, Pageable pageable) {

//        데이터 조회 fetch join
        List<EvalGrade> content = queryFactory
                .selectFrom(qGrade)
                .join(qGrade.emp, qEmployee).fetchJoin()   //사원정보 join
                .where(
                        companyEq(companyId),              //회사필터
                        seasonEq(seasonId),                //시즌필터
                        deptEq(deptId),                    //부서(스냅샷) 필터
                        searchContains(keyword)            //이름/사번 검색
                )
                .orderBy(getOrderSpecifier(sortField))     //정렬
                .offset(pageable.getOffset())              //시작위치
                .limit(pageable.getPageSize())             //한 페이지 개수
                .fetch();   //실행 -> List반환

//        전체건수 조회(페이징 계산용 count만조회-fetchjoinX)
        Long total = queryFactory
                .select(qGrade.count())
                .from(qGrade)
                .join(qGrade.emp, qEmployee) //search 필터에 사원 필드 필요
                .where(
                        companyEq(companyId),
                        seasonEq(seasonId),
                        deptEq(deptId),
                        searchContains(keyword)
                )
                .fetchOne(); //count값으로 단일값

//        Entity -> Dto변환
        List<DraftListItemDto> dtos = new ArrayList<>();
        for (EvalGrade g : content) {
            DraftListItemDto dto = DraftListItemDto.builder()
                    .empNum(g.getEmp().getEmpNum())
                    .name(g.getEmp().getEmpName())
                    .deptName(g.getDeptNameSnapshot())    // 시즌 오픈 시 스냅샷
                    .position(g.getPositionSnapshot())    // 스냅샷
                    .totalScore(g.getTotalScore())        // null -> 미산정
                    .autoGrade(g.getAutoGrade())          // null -> 미산정
                    .build();
            dtos.add(dto);
        }

//        데이터, 페이지정보, 전체건수 합쳐서 page객체 반환
        return new PageImpl<>(dtos, pageable, total != null ? total : 0L);
    }


//  정렬 기준 매핑 Enum사용
    private OrderSpecifier<?> getOrderSpecifier(EvalGradeSortField sortField) {
        if (sortField == null) {
            return qGrade.totalScore.desc();           // 기본: 점수 높은순
        }
        switch (sortField) {
            case EMP_NUM:
                return qEmployee.empNum.asc();          // 사번 오름차순
            case EMP_NAME:
                return qEmployee.empName.asc();         // 이름 가나다순
            case TOTAL_SCORE:
                return qGrade.totalScore.desc();        // 점수 높은순
            case AUTO_GRADE:
                return qGrade.autoGrade.asc();          // 등급 오름차순
            default:
                return qGrade.totalScore.desc();        // 기본값
        }
    }

    //    회사 id필터
    private BooleanExpression companyEq(UUID companyId) {
        if (companyId == null) {
            return null;
        }
        return qGrade.emp.company.companyId.eq(companyId);
    }

    //    시즌 id필터
    private BooleanExpression seasonEq(Long seasonId) {
        if (seasonId == null) {
            return null;
        }
        return qGrade.season.seasonId.eq(seasonId);
    }

    //    부서 id필터 (시즌 오픈 시 스냅샷 기준)
    private BooleanExpression deptEq(Long deptId) {
        if (deptId == null) {
            return null;
        }
        return qGrade.deptIdSnapshot.eq(deptId);
    }

    //  사원이름 또는 사번 검색 (Like)
    private BooleanExpression searchContains(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return qEmployee.empName.contains(search).or(qEmployee.empNum.contains(search));
    }

}

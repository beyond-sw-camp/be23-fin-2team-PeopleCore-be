package com.peoplecore.resign.repository;


import com.peoplecore.resign.domain.*;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class ResignRepositoryImpl implements ResignRepositoryCustom{

    private final JPAQueryFactory queryFactory;
    private final QResign qResign = QResign.resign;

    public ResignRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public Page<Resign>findAllWithFilter(UUID companyId, String keyword, String empStatus, ResignSortField sortField, Pageable pageable){

        List<Resign>content = queryFactory
                .selectFrom(qResign)        //테이블에서 조회
                .join(qResign.employee).fetchJoin()
                .join(qResign.department).fetchJoin()
                .join(qResign.grade).fetchJoin()
                .leftJoin(qResign.title).fetchJoin()
                .where(     //조건절 null이면 자동 무시
                        companyIdEq(companyId),
                        isNotDeleted(),
                        keywordContains(keyword),
                        retireStatusEq(empStatus)
                )
                .orderBy(getOrderSpecifier(sortField))  //정렬(기본: 신청일 최신순)
                .offset(pageable.getOffset())   //시작위치(page*size)
                .limit(pageable.getPageSize()) //한페이지 개수
                .fetch(); //쿼리 실행 -> List<Resign> 반환

//        전체 개수 조회(페이징 계산용 count쿼리따로)
        Long total = queryFactory
                .select(qResign.count())
                .from(qResign)
                .where(
                        companyIdEq(companyId),
                        isNotDeleted(),
                        keywordContains(keyword),
                        retireStatusEq(empStatus)
                )
                .fetchOne(); //단건으로 조회(count)

//        데이터 + 페이지정보 + 전체개수합쳐서 Page객체로 반환
        return new PageImpl<>(content, //데이터 목록
                pageable,  //페이지 정보
                total !=null ? total:0L);
    }

//    정렬기준 - enum 값에 방향까지 포함 (sortField null이면 신청일 최신순 기본)
    private OrderSpecifier<?> getOrderSpecifier(ResignSortField sortField){
        if(sortField == null) return qResign.registeredDate.desc(); //기본: 신청일 최신순
        return switch (sortField){
            case EMP_NUM_ASC -> qResign.employee.empNum.asc();
            case EMP_NUM_DESC -> qResign.employee.empNum.desc();
            case EMP_NAME_ASC -> qResign.employee.empName.asc();
            case EMP_NAME_DESC -> qResign.employee.empName.desc();
            case REGISTERED_DATE_ASC -> qResign.registeredDate.asc();
            case REGISTERED_DATE_DESC -> qResign.registeredDate.desc();
            case RESIGN_DATE_ASC -> qResign.resignDate.asc();
            case RESIGN_DATE_DESC -> qResign.resignDate.desc();
        };
    }

//    회사 id 일치 여부
    private BooleanExpression companyIdEq(UUID companyId){
        return companyId!= null ? qResign.employee.company.companyId.eq(companyId) : null;
    }

//    softDelete제외(isDeleted = false인것만)
    private BooleanExpression isNotDeleted(){
        return qResign.isDeleted.eq(false);
    }

//    이름, 사번에 keyword포함여부(null일시 where에서 무시)
    private BooleanExpression keywordContains(String keyword){
        return keyword != null ? qResign.employee.empName.contains(keyword).or(qResign.employee.empNum.contains(keyword)) : null;
        }


//    재직상태 필터 - 문자열을 enum으로 변환해서 비교
    private BooleanExpression retireStatusEq(String empStatus){
        return empStatus != null ? qResign.retireStatus.eq(RetireStatus.valueOf(empStatus)):null;
    }

}

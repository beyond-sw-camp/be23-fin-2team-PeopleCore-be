package com.peoplecore.evaluation.repository;

import com.peoplecore.employee.domain.QEmployee;
import com.peoplecore.evaluation.domain.EvalGrade;
import com.peoplecore.evaluation.domain.EvalGradeSortField;
import com.peoplecore.evaluation.domain.QEvalGrade;
import com.peoplecore.evaluation.dto.DraftListItemDto;
import com.peoplecore.evaluation.dto.FinalGradeListItemDto;
import com.peoplecore.evaluation.dto.UnassignedEmployeeDto;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
                                                  EvalGradeSortField sortField, Sort.Direction sortDirection, Pageable pageable) {

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
                .orderBy(getOrderSpecifier(sortField, sortDirection))     //정렬
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
                    .totalScore(g.getBiasAdjustedScore()) // Z-score 보정 후 점수 (= 등급 배분 기준)
                    .autoGrade(g.getAutoGrade())          // null -> 미산정
                    .build();
            dtos.add(dto);
        }

//        데이터, 페이지정보, 전체건수 합쳐서 page객체 반환
        return new PageImpl<>(dtos, pageable, total != null ? total : 0L);
    }




    // 7. 보정 페이지 사원 목록
    //  - 엔티티 반환 -> 서비스에서 Calibration 이력 합성 후 DTO 변환
    @Override
    public Page<EvalGrade> searchCalibrationGrades(UUID companyId, Long seasonId,
                                                    Long deptId, String keyword,
                                                    EvalGradeSortField sortField, Sort.Direction sortDirection, Pageable pageable) {

//        데이터 조회 (사원정보 fetch join)
        List<EvalGrade> content = queryFactory
                .selectFrom(qGrade)
                .join(qGrade.emp, qEmployee).fetchJoin()
                .where(
                        companyEq(companyId),
                        seasonEq(seasonId),
                        deptEq(deptId),
                        searchContains(keyword),
                        qGrade.autoGrade.isNotNull()       // 미산정 제외
                )
                .orderBy(getOrderSpecifier(sortField, sortDirection))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

//        전체건수 (페이징용)
        Long total = queryFactory
                .select(qGrade.count())
                .from(qGrade)
                .join(qGrade.emp, qEmployee)
                .where(
                        companyEq(companyId),
                        seasonEq(seasonId),
                        deptEq(deptId),
                        searchContains(keyword),
                        qGrade.autoGrade.isNotNull()
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }



//  정렬 기준 매핑 Enum사용 (direction 이 null 이면 필드별 기본 방향 사용)
    private OrderSpecifier<?> getOrderSpecifier(EvalGradeSortField sortField, Sort.Direction direction) {
        if (sortField == null) {
            return qGrade.biasAdjustedScore.desc();    // 기본: 보정후 점수 높은순 (등급 배분 기준)
        }
        switch (sortField) {
            case EMP_NUM:
                return isDesc(direction, false) ? qEmployee.empNum.desc() : qEmployee.empNum.asc();              // 사번
            case EMP_NAME:
                return isDesc(direction, false) ? qEmployee.empName.desc() : qEmployee.empName.asc();            // 이름
            case DEPT_NAME:
                return isDesc(direction, false) ? qGrade.deptNameSnapshot.desc() : qGrade.deptNameSnapshot.asc(); // 부서 (스냅샷)
            case TOTAL_SCORE:
                return isDesc(direction, true) ? qGrade.biasAdjustedScore.desc() : qGrade.biasAdjustedScore.asc(); // 점수 (보정후 기준)
            case AUTO_GRADE: {
                // 알파벳 정렬(A<D<S)이 아닌 실제 등급 순(S=1>A=2>B=3>C=4>D=5)으로 정렬
                NumberExpression<Integer> autoRank = new CaseBuilder()
                        .when(qGrade.autoGrade.eq("S")).then(1)
                        .when(qGrade.autoGrade.eq("A")).then(2)
                        .when(qGrade.autoGrade.eq("B")).then(3)
                        .when(qGrade.autoGrade.eq("C")).then(4)
                        .when(qGrade.autoGrade.eq("D")).then(5)
                        .otherwise(6);
                return isDesc(direction, true) ? autoRank.asc() : autoRank.desc();
            }
            case FINAL_GRADE: {
                NumberExpression<Integer> finalRank = new CaseBuilder()
                        .when(qGrade.finalGrade.eq("S")).then(1)
                        .when(qGrade.finalGrade.eq("A")).then(2)
                        .when(qGrade.finalGrade.eq("B")).then(3)
                        .when(qGrade.finalGrade.eq("C")).then(4)
                        .when(qGrade.finalGrade.eq("D")).then(5)
                        .otherwise(6);
                return isDesc(direction, true) ? finalRank.asc() : finalRank.desc();
            }
            default:
                return qGrade.biasAdjustedScore.desc();    // 기본값 (보정후 기준)
        }
    }

    // direction 이 null 이면 필드별 기본 방향(defaultDesc) 사용
    private boolean isDesc(Sort.Direction direction, boolean defaultDesc) {
        return direction == null ? defaultDesc : direction == Sort.Direction.DESC;
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

    //  미산정자 필터 (null=전체 / true=autoGrade null만 / false=autoGrade not null만)
    private BooleanExpression unscoredEq(Boolean unscoredOnly) {
        if (unscoredOnly == null) {
            return null;
        }
        return unscoredOnly ? qGrade.autoGrade.isNull() : qGrade.autoGrade.isNotNull();
    }


    // 11. 미제출·미산정 직원 목록 (finalGrade IS NULL 대상)
    //  - 회사/시즌 범위 + 부서 필터 + 정렬/페이징
    //  - 정렬 기본값: 사번 오름차순
    @Override
    public Page<UnassignedEmployeeDto> searchUnassigned(UUID companyId, Long seasonId,
                                                         Long deptId,
                                                         EvalGradeSortField sortField,
                                                         Sort.Direction sortDirection,
                                                         Pageable pageable) {

        List<EvalGrade> content = queryFactory
                .selectFrom(qGrade)
                .join(qGrade.emp, qEmployee).fetchJoin()
                .where(
                        companyEq(companyId),
                        seasonEq(seasonId),
                        deptEq(deptId),
                        qGrade.finalGrade.isNull()         // 미산정만
                )
                .orderBy(getUnassignedOrder(sortField, sortDirection))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(qGrade.count())
                .from(qGrade)
                .join(qGrade.emp, qEmployee)
                .where(
                        companyEq(companyId),
                        seasonEq(seasonId),
                        deptEq(deptId),
                        qGrade.finalGrade.isNull()
                )
                .fetchOne();

        List<UnassignedEmployeeDto> dtos = new ArrayList<>();
        for (EvalGrade g : content) {
            dtos.add(UnassignedEmployeeDto.builder()
                    .empId(g.getEmp().getEmpId())
                    .empNum(g.getEmp().getEmpNum())
                    .empName(g.getEmp().getEmpName())
                    .deptName(g.getDeptNameSnapshot())
                    .position(g.getPositionSnapshot())
                    .build());
        }
        return new PageImpl<>(dtos, pageable, total != null ? total : 0L);
    }

    // 미산정 목록 전용 정렬 (기본: 사번 오름차순 — 점수 컬럼 없음)
    private OrderSpecifier<?> getUnassignedOrder(EvalGradeSortField sortField, Sort.Direction direction) {
        if (sortField == null) return qEmployee.empNum.asc();
        boolean desc = isDesc(direction, false);
        switch (sortField) {
            case EMP_NUM:   return desc ? qEmployee.empNum.desc() : qEmployee.empNum.asc();
            case EMP_NAME:  return desc ? qEmployee.empName.desc() : qEmployee.empName.asc();
            case DEPT_NAME: return desc ? qGrade.deptNameSnapshot.desc() : qGrade.deptNameSnapshot.asc();
            case POSITION:  return desc ? qGrade.positionSnapshot.desc() : qGrade.positionSnapshot.asc();
            default:        return qEmployee.empNum.asc();
        }
    }


    // 13. 평가 결과 목록 (미산정자 포함, unscoredOnly 로 분기)
    //  - 시즌/회사 범위 + 부서·키워드·미산정자 필터 + 정렬/페이징
    @Override
    public Page<FinalGradeListItemDto> searchFinalList(UUID companyId, Long seasonId,
                                                       Long deptId, String keyword,
                                                       Boolean unscoredOnly,
                                                       EvalGradeSortField sortField, Sort.Direction sortDirection, Pageable pageable) {

//        데이터 조회 fetch join
        List<EvalGrade> content = queryFactory
                .selectFrom(qGrade)
                .join(qGrade.emp, qEmployee).fetchJoin()   //사원정보 join
                .where(
                        companyEq(companyId),              //회사필터
                        seasonEq(seasonId),                //시즌필터
                        deptEq(deptId),                    //부서(스냅샷) 필터
                        searchContains(keyword),           //이름/사번 검색
                        unscoredEq(unscoredOnly)           //미산정자 포함/배제/단독
                )
                .orderBy(getOrderSpecifier(sortField, sortDirection))     //정렬
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
                        searchContains(keyword),
                        unscoredEq(unscoredOnly)
                )
                .fetchOne(); //count값으로 단일값

//        Entity -> Dto반환
        List<FinalGradeListItemDto> dtos = new ArrayList<>();
        for (EvalGrade g : content) {
            FinalGradeListItemDto dto = FinalGradeListItemDto.builder()
                    .gradeId(g.getGradeId())
                    .empNum(g.getEmp().getEmpNum())
                    .empName(g.getEmp().getEmpName())
                    .deptName(g.getDeptNameSnapshot())    // 시즌 오픈 시 스냅샷
                    .position(g.getPositionSnapshot())    // 스냅샷
                    .totalScore(g.getBiasAdjustedScore()) // Z-score 보정 후 점수 (= 등급 배분 기준)
                    .autoGrade(g.getAutoGrade())          // null -> 미산정자 (프론트 배지 분기)
                    .finalGrade(g.getFinalGrade())        // 보정 반영 / 확정 후 박제값
                    .isCalibrated(Boolean.TRUE.equals(g.getIsCalibrated()))
                    .build();
            dtos.add(dto);
        }

//        데이터, 페이지정보, 전체건수 합쳐서 page객체 반환
        return new PageImpl<>(dtos, pageable, total != null ? total : 0L);
    }

}

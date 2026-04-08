package com.peoplecore.salarycontract.repository;

import com.peoplecore.employee.domain.QEmployee;
import com.peoplecore.salarycontract.domain.QSalaryContract;
import com.peoplecore.salarycontract.domain.SalaryContract;
import com.peoplecore.salarycontract.domain.SalaryContractSortField;
import com.peoplecore.salarycontract.dto.SalaryContractListResDto;
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

@Repository
public class SalaryContractRepositoryImpl implements SalaryContractRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final QSalaryContract qSalaryContract = QSalaryContract.salaryContract;
    private final QEmployee qEmployee = QEmployee.employee;

    public SalaryContractRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }


    @Override
    public Page<SalaryContractListResDto> findAllWithFilter(UUID companyId, String search, String year, SalaryContractSortField sortField, Pageable pageable) {

        //    데이터 조회 fetch join
        List<SalaryContract> content = queryFactory
                .selectFrom(qSalaryContract)
                .join(qSalaryContract.employee, qEmployee).fetchJoin() //사원정보 join
                .join(qEmployee.dept).fetchJoin() // 부서 join
                .join(qEmployee.grade).fetchJoin() // 직급 join
                .leftJoin(qEmployee.title).fetchJoin() //직책 join
                .where(
                        companyEq(companyId),        //회사필터
                        searchContains(search),     //이름/사번 검색
                        yearEq(year)                //연도필터
                )
                .orderBy(getOrderSpecifier(sortField))  //정렬
                .offset(pageable.getOffset())       //시작위치
                .limit(pageable.getPageSize())      //한 페이지 개수
                .fetch();   //실행 -> List반환

//        전체건수 조회(페이징 계산용 count만조회-fetchjoinX)
        Long total = queryFactory
                .select(qSalaryContract.count())
                .from(qSalaryContract)
                .join(qSalaryContract.employee, qEmployee) //search 필터에 사원 필드 필요
                .where(
                        companyEq(companyId), //회사Id일치
                        searchContains(search), //검색어 필터
                        yearEq(year) //연도필터
                )
                .fetchOne(); //count값으로 단일값

//        Entity -> Dto반환
        List<SalaryContractListResDto>dtos =new ArrayList<>();
        for(SalaryContract c : content){
         String titleName = null;   //현재 직책 nullable
            if(c.getEmployee().getTitle() !=null){
                titleName = c.getEmployee().getTitle().getTitleName();
            }

            SalaryContractListResDto dto = SalaryContractListResDto.builder()
                    .id(c.getContractId())
                    .empNum(c.getEmployee().getEmpNum())
                    .empName(c.getEmployee().getEmpName())
                    .department(c.getEmployee().getDept().getDeptName())
                    .rank(c.getEmployee().getGrade().getGradeName())
                    .position(titleName)
                    .employmentType(c.getEmployee().getEmpType())
                    .contractStart(c.getApplyFrom())
                                    .build();

            dtos.add(dto);
        }

//        데이터, 페이지정보, 전체건수 합쳐서 page객체 반환
        return new PageImpl<>(dtos, pageable, total != null ? total : 0L);
    }



//  정렬 기준 매핑  Enum사용
    private OrderSpecifier<?>getOrderSpecifier(SalaryContractSortField sortField) {
        if (sortField == null) {
            return qSalaryContract.contractId.desc(); // 최신계약순
        }
        switch (sortField){
            case EMP_NUM:
                return qEmployee.empNum.asc(); // 사번 오름차순
            case EMP_NAME:
                return qEmployee.empName.asc(); //이름 가나다순
            case CONTRACT_START:
                return qSalaryContract.applyFrom.desc();   //계약 최신순
            default:
                return qSalaryContract.contractId.desc();   //계약ID 기준 최신순 (기본 정렬)

        }
    }

    //    회사 id필터
    private BooleanExpression companyEq(UUID companyId) {
        if (companyId == null) {
            return null;
        }
        return qSalaryContract.companyId.eq(companyId);
    }

    //  사원이름 또는 사번 검색 (Like)
    private BooleanExpression searchContains(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return qEmployee.empName.contains(search).or(qEmployee.empNum.contains(search));
    }

    //    계약년도 필터
    private BooleanExpression yearEq(String year) {
        if (year == null || year.isBlank()) {
            return null;
        }
        return qSalaryContract.contractYear.eq(Integer.parseInt(year));
    }

}

package com.peoplecore.vacation.repository;

import com.peoplecore.vacation.dto.BalanceExpiryQueryDto;
import com.peoplecore.vacation.entity.VacationBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/* 휴가 잔여 레포 - 단순 단건 조회만. 복잡 조회는 VacationBalanceQueryRepository */
@Repository
public interface VacationBalanceRepository extends JpaRepository<VacationBalance, Long> {

    /*
     * 사원 특정 유형 특정 연도 잔여 단건 조회
     * 용도: 적립 잡, 결재 승인 차감, 신청 시 잔여 검증
     * 인덱스: uk_vacation_balance_company_emp_type_year (커버)
     * 반환: Optional - 첫 적립 전이면 empty (호출부가 createNew INSERT)
     */
    @Query("""
            SELECT b FROM VacationBalance b
             WHERE b.companyId = :companyId
               AND b.employee.empId = :empId
               AND b.vacationType.typeId = :typeId
               AND b.balanceYear = :year
            """)
    Optional<VacationBalance> findOne(@Param("companyId") UUID companyId,
                                      @Param("empId") Long empId,
                                      @Param("typeId") Long typeId,
                                      @Param("year") Integer year);

    /*
     * 급여팀 호환 메서드 - LeaveAllowanceService 가 명시적으로 typeId 지정
     * findOne 위임. 가독성 위해 별도 메서드명
     */
    default Optional<VacationBalance> findForAllowance(UUID companyId, Long empId, Long typeId, Integer year) {
        return findOne(companyId, empId, typeId, year);
    }

    /*
     * 사원 + 유형 + 연도 범위 일괄 조회 - AnnualTransition 월차 소멸 시 N+1 방지
     * 조건: balance_year BETWEEN startYear AND endYear
     */
    @Query("""
            SELECT b FROM VacationBalance b
             WHERE b.companyId = :companyId
               AND b.employee.empId = :empId
               AND b.vacationType.typeId = :typeId
               AND b.balanceYear BETWEEN :startYear AND :endYear
             ORDER BY b.balanceYear ASC
            """)
    List<VacationBalance> findAllByYearRange(@Param("companyId") UUID companyId,
                                             @Param("empId") Long empId,
                                             @Param("typeId") Long typeId,
                                             @Param("startYear") Integer startYear,
                                             @Param("endYear") Integer endYear);

    /*
     * 특정 휴가 유형을 참조하는 잔여 존재 여부
     * 용도: 휴가 유형 물리 삭제 시 FK 참조 체크
     * 반환: true 면 삭제 차단 (VACATION_TYPE_IN_USE)
     */
    boolean existsByVacationType_TypeId(Long typeId);

    /*
     * 사원 다건 + 특정 유형 + 연도 balance 의 시작/만료일 projection 조회
     * 용도: 부서별 사원 상세 테이블 "연차 사용기간" (grantedAt ~ expiresAt) 표시
     * N+1 방지: IN 절 + DTO projection (엔티티 로드 없음)
     * 반환: balance 존재하는 사원만. 없는 사원은 호출부에서 null 폴백
     */
    @Query("""
            SELECT new com.peoplecore.vacation.dto.BalanceExpiryQueryDto(
                       b.employee.empId, b.grantedAt, b.expiresAt)
              FROM VacationBalance b
             WHERE b.companyId = :companyId
               AND b.vacationType.typeCode = :typeCode
               AND b.balanceYear = :year
               AND b.employee.empId IN :empIds
            """)
    List<BalanceExpiryQueryDto> findExpiryByEmpsAndType(@Param("companyId") UUID companyId,
                                                       @Param("typeCode") String typeCode,
                                                       @Param("year") Integer year,
                                                       @Param("empIds") Collection<Long> empIds);
}
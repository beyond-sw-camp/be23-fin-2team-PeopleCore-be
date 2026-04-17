package com.peoplecore.vacation.repository;

import com.peoplecore.vacation.entity.VacationBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
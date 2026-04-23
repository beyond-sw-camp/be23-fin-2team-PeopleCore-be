package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.LeaveAllowance;
import com.peoplecore.pay.enums.AllowanceStatus;
import com.peoplecore.pay.enums.AllowanceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LeaveAllowanceRepository extends JpaRepository<LeaveAllowance, Long> {

//    특정 사원 중복 체크
    boolean existsByCompany_CompanyIdAndEmployee_EmpIdAndYearAndAllowanceType(UUID companyId, Long empId, Integer year, AllowanceType type);

//    선택된 ID 목록으로 조회
    List<LeaveAllowance> findByAllowanceIdInAndCompany_CompanyId(List<Long> allowanceId, UUID companyId);

    // 연도 + 유형별 목록 (JOIN FETCH)
    @Query("SELECT la FROM LeaveAllowance la " +
            "JOIN FETCH la.employee e " +
            "JOIN FETCH e.dept " +
            "JOIN FETCH e.grade " +
            "WHERE la.company.companyId = :companyId " +
            "AND la.year = :year " +
            "AND la.allowanceType = :type " +
            "ORDER BY e.empName")
    List<LeaveAllowance> findAllByCompanyAndYearAndType(UUID companyId, Integer year, AllowanceType type);

    // 상태별 카운트
    @Query("SELECT COUNT(la) FROM LeaveAllowance la " +
            "WHERE la.company.companyId = :companyId " +
            "AND la.year = :year " +
            "AND la.allowanceType = :type " +
            "AND la.status = :status")
    long countByStatus(
            @Param("companyId") UUID companyId,
            @Param("year") Integer year,
            @Param("type") AllowanceType type,
            @Param("status") AllowanceStatus status
    );

//    퇴직금 정산을 위한 상태값(CALCULATED/APPLIED)으로 가져와서 service에서 기간 필터링에 사용
    @Query("SELECT la FROM LeaveAllowance la " +
            "JOIN FETCH la.employee e " +
            "WHERE la.company.companyId = :companyId " +
            "AND la.employee.empId = :empId " +
            "AND la.status IN (:statuses)")
    List<LeaveAllowance> findForSeverance(
            @Param("companyId") UUID companyId,
            @Param("empId") Long empID,
            @Param("statuses") List<AllowanceStatus> statuses);
}
